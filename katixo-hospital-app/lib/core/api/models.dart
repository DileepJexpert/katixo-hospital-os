/// Typed DTOs for the Spring Boot backend API.
/// Shapes mirror the backend controllers exactly — update together.
library;

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

  factory ApiResponse.fromJson(
      Map<String, dynamic> json, T Function(dynamic) fromJsonT) {
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

/// ============ Auth (matches AuthController) ============

class CurrentUser {
  const CurrentUser({
    required this.userId,
    required this.username,
    required this.name,
    required this.role,
    required this.tenantId,
    required this.hospitalGroupId,
    required this.branchId,
    this.staffId,
  });

  final String userId;
  final String username;
  final String name;
  final String role;
  final String tenantId;
  final String hospitalGroupId;
  final String branchId;
  final int? staffId;

  bool hasRole(String r) => role == r;
  bool hasAnyRole(List<String> rs) => rs.contains(role);

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      userId: json['userId'] as String,
      username: json['username'] as String,
      name: json['name'] as String? ?? '',
      role: json['role'] as String,
      tenantId: json['tenantId'] as String,
      hospitalGroupId: json['hospitalGroupId'] as String,
      branchId: json['branchId'] as String,
      staffId: json['staffId'] as int?,
    );
  }

  Map<String, dynamic> toJson() => {
        'userId': userId,
        'username': username,
        'name': name,
        'role': role,
        'tenantId': tenantId,
        'hospitalGroupId': hospitalGroupId,
        'branchId': branchId,
        if (staffId != null) 'staffId': staffId,
      };
}

class LoginRequest {
  const LoginRequest({required this.username, required this.password, this.mfaCode});

  final String username;
  final String password;

  /// TOTP code — sent only when the account has two-factor enabled.
  final String? mfaCode;

  Map<String, dynamic> toJson() => {
        'username': username,
        'password': password,
        if (mfaCode != null && mfaCode!.isNotEmpty) 'mfaCode': mfaCode,
      };
}

class LoginResponse {
  const LoginResponse({required this.token, required this.user});

  final String token;
  final CurrentUser user;

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      token: json['token'] as String,
      user: CurrentUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

/// ============ Patient (matches PatientController / PatientDTO) ============

class PatientRegistrationRequest {
  const PatientRegistrationRequest({
    required this.firstName,
    required this.lastName,
    required this.mobile,
    required this.dateOfBirth,
    required this.gender,
    required this.privacyConsentGiven,
    this.middleName,
    this.email,
    this.addressLine1,
    this.city,
    this.state,
    this.pincode,
    this.dataSharingConsent = false,
  });

  final String firstName;
  final String lastName;
  final String mobile;
  final String dateOfBirth; // "YYYY-MM-DD"
  final String gender; // MALE | FEMALE | OTHER | PREFER_NOT_TO_SAY
  final bool privacyConsentGiven;
  final String? middleName;
  final String? email;
  final String? addressLine1;
  final String? city;
  final String? state;
  final String? pincode;
  final bool dataSharingConsent;

  Map<String, dynamic> toJson() => {
        'firstName': firstName,
        'lastName': lastName,
        'mobile': mobile,
        'dateOfBirth': dateOfBirth,
        'gender': gender,
        'privacyConsentGiven': privacyConsentGiven,
        if (middleName != null) 'middleName': middleName,
        if (email != null) 'email': email,
        if (addressLine1 != null) 'addressLine1': addressLine1,
        if (city != null) 'city': city,
        if (state != null) 'state': state,
        if (pincode != null) 'pincode': pincode,
        'dataSharingConsent': dataSharingConsent,
      };
}

class PatientResponse {
  const PatientResponse({
    required this.id,
    required this.uhid,
    required this.fullName,
    required this.mobile,
    this.age,
    this.gender,
  });

  factory PatientResponse.fromJson(Map<String, dynamic> json) {
    return PatientResponse(
      id: json['id'] as int,
      uhid: json['uhid'] as String,
      fullName: json['fullName'] as String? ??
          '${json['firstName']} ${json['lastName']}',
      mobile: json['mobile'] as String? ?? '',
      age: json['age'] as int?,
      gender: json['gender'] as String?,
    );
  }

  final int id;
  final String uhid;
  final String fullName;
  final String mobile;
  final int? age;
  final String? gender;
}
