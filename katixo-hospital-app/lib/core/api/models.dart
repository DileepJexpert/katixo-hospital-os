/// Typed DTOs for the Spring Boot backend API responses.
/// Keep these in sync with the backend via contract tests.

class ApiResponse<T> {
  const ApiResponse({
    required this.success,
    required this.status,
    required this.message,
    required this.data,
    this.correlationId,
    this.error,
  });

  final bool success;
  final int status;
  final String message;
  final T data;
  final String? correlationId;
  final String? error;

  factory ApiResponse.fromJson(Map<String, dynamic> json, T Function(dynamic) fromJsonT) {
    return ApiResponse(
      success: json['success'] as bool,
      status: json['status'] as int,
      message: json['message'] as String,
      data: fromJsonT(json['data']),
      correlationId: json['correlationId'] as String?,
      error: json['error'] as String?,
    );
  }
}

class ErrorResponse {
  const ErrorResponse({
    required this.error,
    required this.message,
    this.status = 400,
    this.details = const [],
  });

  final String error;
  final String message;
  final int status;
  final List<String> details;

  factory ErrorResponse.fromJson(Map<String, dynamic> json) {
    return ErrorResponse(
      error: json['error'] as String? ?? 'UNKNOWN_ERROR',
      message: json['message'] as String? ?? 'An error occurred',
      status: json['status'] as int? ?? 400,
      details: List<String>.from(json['details'] as List? ?? []),
    );
  }
}

/// JWT token payload (decoded on client).
class CurrentUser {
  const CurrentUser({
    required this.userId,
    required this.username,
    required this.email,
    required this.roles,
    required this.tenantId,
    required this.hospitalGroupId,
    required this.branchId,
  });

  final String userId;
  final String username;
  final String email;
  final List<String> roles;
  final String tenantId;
  final String hospitalGroupId;
  final String branchId;

  bool hasRole(String role) => roles.contains(role);
  bool hasAnyRole(List<String> roles) => roles.any(hasRole);
}

class LoginRequest {
  const LoginRequest({
    required this.username,
    required this.password,
  });

  Map<String, dynamic> toJson() => {
        'username': username,
        'password': password,
      };

  final String username;
  final String password;
}

class LoginResponse {
  const LoginResponse({
    required this.token,
    required this.user,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      token: json['token'] as String,
      user: _parseCurrentUser(json['user'] as Map<String, dynamic>),
    );
  }

  final String token;
  final CurrentUser user;
}

/// Helper to parse JWT claims into CurrentUser (on client, not decoded server-side).
CurrentUser _parseCurrentUser(Map<String, dynamic> claims) {
  return CurrentUser(
    userId: claims['sub'] as String,
    username: claims['username'] as String? ?? '',
    email: claims['email'] as String? ?? '',
    roles: List<String>.from(claims['roles'] as List? ?? []),
    tenantId: claims['tenant_id'] as String? ?? '',
    hospitalGroupId: claims['hospital_group_id'] as String? ?? '',
    branchId: claims['branch_id'] as String? ?? '',
  );
}

/// ============ Patient / Registration ============

class PatientRegistrationRequest {
  const PatientRegistrationRequest({
    required this.firstName,
    required this.lastName,
    required this.mobile,
    required this.dateOfBirth,
    required this.gender,
    required this.privacyConsentGiven,
    this.address,
    this.identifiers = const [],
  });

  Map<String, dynamic> toJson() => {
        'firstName': firstName,
        'lastName': lastName,
        'mobile': mobile,
        'dateOfBirth': dateOfBirth,
        'gender': gender,
        'privacyConsentGiven': privacyConsentGiven,
        'address': address,
        'identifiers': identifiers.map((i) => i.toJson()).toList(),
      };

  final String firstName;
  final String lastName;
  final String mobile;
  final String dateOfBirth; // "YYYY-MM-DD"
  final String gender;
  final bool privacyConsentGiven;
  final String? address;
  final List<PatientIdentifierRequest> identifiers;
}

class PatientIdentifierRequest {
  const PatientIdentifierRequest({
    required this.type,
    required this.value,
  });

  Map<String, dynamic> toJson() => {
        'type': type,
        'value': value,
      };

  final String type; // AADHAAR, PAN, VOTER_ID, PASSPORT, etc.
  final String value;
}

class PatientResponse {
  const PatientResponse({
    required this.id,
    required this.uhid,
    required this.firstName,
    required this.lastName,
    required this.mobile,
    required this.status,
  });

  factory PatientResponse.fromJson(Map<String, dynamic> json) {
    return PatientResponse(
      id: json['id'] as int,
      uhid: json['uhid'] as String,
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      mobile: json['mobile'] as String,
      status: json['status'] as String,
    );
  }

  final int id;
  final String uhid;
  final String firstName;
  final String lastName;
  final String mobile;
  final String status;

  String get fullName => '$firstName $lastName';
}
