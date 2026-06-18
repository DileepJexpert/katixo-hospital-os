import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../core/auth/auth_state.dart';
import 'models.dart';

/// HTTP client with JWT auth, tenant headers, error handling, retries.
/// Every API call goes through here — no raw http.post() anywhere else.
class ApiClient {
  ApiClient(
    this.baseUrl, {
    required this.authState,
    this.onUnauthorized,
  }) : _client = http.Client();

  final String baseUrl;
  final AuthState authState;
  final VoidCallback? onUnauthorized;
  final http.Client _client;

  static const _maxRetries = 3;
  static const _retryDelayMs = 500;

  /// Common headers: JWT + tenant context + correlation ID.
  Map<String, String> _headers([String? correlationId, String accept = 'application/json']) {
    final token = authState.token;
    final user = authState.currentUser;

    return {
      'Content-Type': 'application/json',
      'Accept': accept,
      if (token != null) 'Authorization': 'Bearer $token',
      if (user != null) ...{
        'X-Tenant-Id': user.tenantId,
        'X-Group-Id': user.hospitalGroupId,
        'X-Branch-Id': user.branchId,
      },
      'X-Correlation-Id': correlationId ?? _generateCorrelationId(),
      'X-Client-Version': '0.1.0',
    };
  }

  /// GET with automatic retries and error handling.
  Future<T> get<T>(
    String endpoint, {
    required T Function(dynamic) fromJson,
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$endpoint');
    return _retryable<T>(() async {
      final response = await _client.get(uri, headers: _headers(correlationId));
      return _handleResponse(response, fromJson);
    });
  }

  /// GET raw bytes (e.g. a PDF) with the same auth + tenant headers and retry
  /// policy as [get]. Returns the response body bytes on 2xx, otherwise throws
  /// [ApiException]/[UnauthorizedException] like the JSON path. Used for the
  /// server-rendered PDFs (bill receipt, expense voucher, payslip, lab report).
  Future<Uint8List> getBytes(
    String endpoint, {
    String accept = 'application/pdf',
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$endpoint');
    return _retryable<Uint8List>(() async {
      final response =
          await _client.get(uri, headers: _headers(correlationId, accept));
      if (response.statusCode == 401) {
        onUnauthorized?.call();
        throw UnauthorizedException('Session expired');
      }
      if (response.statusCode >= 200 && response.statusCode < 300) {
        return response.bodyBytes;
      }
      throw ApiException(_parseError(response));
    });
  }

  /// POST with request body.
  ///
  /// NOT auto-retried: a POST that committed server-side but whose response was
  /// lost would be duplicated on retry (double payment/sale/journal). Until the
  /// command APIs accept an Idempotency-Key, write requests run exactly once and
  /// surface the error to the caller.
  Future<T> post<T>(
    String endpoint,
    Object body, {
    required T Function(dynamic) fromJson,
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$endpoint');
    final response = await _client.post(
      uri,
      headers: _headers(correlationId),
      body: jsonEncode(body),
    );
    return _handleResponse(response, fromJson);
  }

  /// POST a multipart upload (the `file` part plus optional text [fields]) with
  /// the SAME auth + tenant headers as [post]. Not auto-retried (an upload that
  /// committed but lost its response would be duplicated on retry). The server
  /// is expected to return the standard ApiResponse envelope; this returns the
  /// decoded `data` map. The byte content type is sent as a `contentType` form
  /// field (we avoid a hard dependency on `http_parser`'s MediaType on the part).
  Future<Map<String, dynamic>> uploadBytes(
    String path, {
    required Uint8List bytes,
    required String filename,
    String? contentType,
    Map<String, String>? fields,
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final request = http.MultipartRequest('POST', uri);
    // Reuse the JSON header set, minus Content-Type (multipart sets its own boundary).
    final headers = _headers(correlationId)..remove('Content-Type');
    request.headers.addAll(headers);
    if (fields != null) request.fields.addAll(fields);
    if (contentType != null && contentType.isNotEmpty) {
      request.fields['contentType'] = contentType;
    }
    request.files.add(
      http.MultipartFile.fromBytes('file', bytes, filename: filename),
    );

    final streamed = await _client.send(request);
    final response = await http.Response.fromStream(streamed);

    if (response.statusCode == 401) {
      onUnauthorized?.call();
      throw UnauthorizedException('Session expired');
    }
    if (response.statusCode >= 200 && response.statusCode < 300) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      if (!(json['success'] as bool)) {
        throw ApiException(ErrorResponse.fromJson(json));
      }
      return (json['data'] as Map<String, dynamic>?) ?? <String, dynamic>{};
    }
    throw ApiException(_parseError(response));
  }

  /// PUT with request body. Not auto-retried (see [post]).
  Future<T> put<T>(
    String endpoint,
    Object body, {
    required T Function(dynamic) fromJson,
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$endpoint');
    final response = await _client.put(
      uri,
      headers: _headers(correlationId),
      body: jsonEncode(body),
    );
    return _handleResponse(response, fromJson);
  }

  /// DELETE. Not auto-retried (see [post]).
  Future<T> delete<T>(
    String endpoint, {
    required T Function(dynamic) fromJson,
    String? correlationId,
  }) async {
    final uri = Uri.parse('$baseUrl$endpoint');
    final response = await _client.delete(uri, headers: _headers(correlationId));
    return _handleResponse(response, fromJson);
  }

  /// Retry with exponential backoff on transient errors.
  Future<T> _retryable<T>(Future<T> Function() call) async {
    for (var attempt = 1; attempt <= _maxRetries; attempt++) {
      try {
        return await call();
      } catch (e) {
        if (attempt == _maxRetries) rethrow;
        if (!_isRetryable(e)) rethrow;
        await Future.delayed(
          Duration(milliseconds: _retryDelayMs * (1 << (attempt - 1))),
        );
      }
    }
    throw StateError('Unreachable');
  }

  /// Parse response with error handling.
  T _handleResponse<T>(
    http.Response response,
    T Function(dynamic) fromJson,
  ) {
    if (response.statusCode == 401) {
      onUnauthorized?.call();
      throw UnauthorizedException('Session expired');
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      if (!json['success'] as bool) {
        throw ApiException(
          ErrorResponse.fromJson(json),
        );
      }
      return ApiResponse.fromJson(json, fromJson).data;
    }

    final error = _parseError(response);
    throw ApiException(error);
  }

  /// Extract error from response body or synthesize from status.
  ErrorResponse _parseError(http.Response response) {
    try {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      return ErrorResponse.fromJson(json);
    } catch (_) {
      // Fallback if body is not JSON (e.g., server crash HTML).
      return ErrorResponse(
        error: 'HTTP_${response.statusCode}',
        message: response.reasonPhrase ?? 'Request failed',
        status: response.statusCode,
      );
    }
  }

  /// Determine if error is retryable (transient).
  bool _isRetryable(Object e) {
    if (e is UnauthorizedException) return false;
    if (e is ApiException) {
      final status = e.error.status;
      return status == 408 || status == 429 || status >= 500;
    }
    return true; // Network errors, timeouts, etc.
  }

  String _generateCorrelationId() {
    // In production, use uuid package; for now use timestamp + random.
    return 'mobile-${DateTime.now().millisecondsSinceEpoch}';
  }

  void dispose() => _client.close();
}

class ApiException implements Exception {
  ApiException(this.error);

  final ErrorResponse error;

  @override
  String toString() => '${error.error}: ${error.message}';
}

class UnauthorizedException implements Exception {
  UnauthorizedException(this.message);

  final String message;

  @override
  String toString() => 'Unauthorized: $message';
}
