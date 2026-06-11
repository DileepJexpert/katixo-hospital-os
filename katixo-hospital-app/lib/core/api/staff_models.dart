class StaffResponse {
  final int id;
  final String firstName;
  final String lastName;
  final String email;
  final String phone;
  final String role;
  final String department;
  final String? specialization;
  final DateTime? dateOfJoining;
  final bool isActive;
  final bool canApproveDiscount;
  final bool canApproveDischargeSummary;
  final bool canApproveLabReport;
  final String? notes;

  StaffResponse({
    required this.id,
    required this.firstName,
    required this.lastName,
    required this.email,
    required this.phone,
    required this.role,
    required this.department,
    this.specialization,
    this.dateOfJoining,
    required this.isActive,
    required this.canApproveDiscount,
    required this.canApproveDischargeSummary,
    required this.canApproveLabReport,
    this.notes,
  });

  factory StaffResponse.fromJson(Map<String, dynamic> json) {
    return StaffResponse(
      id: json['id'] as int,
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      email: json['email'] as String,
      phone: json['phone'] as String,
      role: json['role'] as String,
      department: json['department'] as String,
      specialization: json['specialization'] as String?,
      dateOfJoining: json['dateOfJoining'] != null
          ? DateTime.parse(json['dateOfJoining'] as String)
          : null,
      isActive: json['isActive'] as bool? ?? true,
      canApproveDiscount: json['canApproveDiscount'] as bool? ?? false,
      canApproveDischargeSummary:
          json['canApproveDischargeSummary'] as bool? ?? false,
      canApproveLabReport: json['canApproveLabReport'] as bool? ?? false,
      notes: json['notes'] as String?,
    );
  }

  String get fullName => '$firstName $lastName';

  String get statusDisplay => isActive ? 'Active' : 'Inactive';
}

class CreateStaffRequest {
  final String firstName;
  final String lastName;
  final String email;
  final String phone;
  final String role;
  final String department;
  final String? specialization;
  final DateTime? dateOfJoining;
  final bool canApproveDiscount;
  final bool canApproveDischargeSummary;
  final bool canApproveLabReport;
  final String? notes;

  CreateStaffRequest({
    required this.firstName,
    required this.lastName,
    required this.email,
    required this.phone,
    required this.role,
    required this.department,
    this.specialization,
    this.dateOfJoining,
    this.canApproveDiscount = false,
    this.canApproveDischargeSummary = false,
    this.canApproveLabReport = false,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'firstName': firstName,
        'lastName': lastName,
        'email': email,
        'phone': phone,
        'role': role,
        'department': department,
        'specialization': specialization,
        'dateOfJoining': dateOfJoining?.toIso8601String(),
        'canApproveDiscount': canApproveDiscount,
        'canApproveDischargeSummary': canApproveDischargeSummary,
        'canApproveLabReport': canApproveLabReport,
        'notes': notes,
      };
}

class UpdateStaffRequest {
  final String? firstName;
  final String? lastName;
  final String? phone;
  final String? department;
  final String? specialization;
  final bool? canApproveDiscount;
  final bool? canApproveDischargeSummary;
  final bool? canApproveLabReport;

  UpdateStaffRequest({
    this.firstName,
    this.lastName,
    this.phone,
    this.department,
    this.specialization,
    this.canApproveDiscount,
    this.canApproveDischargeSummary,
    this.canApproveLabReport,
  });

  Map<String, dynamic> toJson() => {
        if (firstName != null) 'firstName': firstName,
        if (lastName != null) 'lastName': lastName,
        if (phone != null) 'phone': phone,
        if (department != null) 'department': department,
        if (specialization != null) 'specialization': specialization,
        if (canApproveDiscount != null) 'canApproveDiscount': canApproveDiscount,
        if (canApproveDischargeSummary != null)
          'canApproveDischargeSummary': canApproveDischargeSummary,
        if (canApproveLabReport != null)
          'canApproveLabReport': canApproveLabReport,
      };
}
