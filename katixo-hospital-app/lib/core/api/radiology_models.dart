/// Radiology DTOs.

class RadiologyTest {
  const RadiologyTest({
    required this.id,
    required this.testCode,
    required this.testName,
    required this.rate,
  });

  final int id;
  final String testCode;
  final String testName;
  final num rate;

  factory RadiologyTest.fromJson(Map<String, dynamic> json) {
    return RadiologyTest(
      id: json['id'] as int,
      testCode: json['testCode'] as String,
      testName: json['testName'] as String,
      rate: json['rate'] as num,
    );
  }
}

class RadiologyOrderItem {
  const RadiologyOrderItem({
    required this.itemId,
    required this.testCode,
    required this.testName,
    required this.itemStatus,
    this.imageUrl,
    this.reportText,
    this.reportStatus,
    this.reportedAt,
  });

  final int itemId;
  final String testCode;
  final String testName;
  final String itemStatus;
  final String? imageUrl;
  final String? reportText;
  final String? reportStatus;
  final String? reportedAt;

  factory RadiologyOrderItem.fromJson(Map<String, dynamic> json) {
    return RadiologyOrderItem(
      itemId: json['itemId'] as int,
      testCode: json['testCode'] as String,
      testName: json['testName'] as String,
      itemStatus: json['itemStatus'] as String,
      imageUrl: json['imageUrl'] as String?,
      reportText: json['reportText'] as String?,
      reportStatus: json['reportStatus'] as String?,
      reportedAt: json['reportedAt'] as String?,
    );
  }
}

class EnterRadiologyReportRequest {
  const EnterRadiologyReportRequest({
    required this.reportText,
    this.imageUrl,
  });

  final String reportText;
  final String? imageUrl;

  Map<String, dynamic> toJson() => {
        'reportText': reportText,
        if (imageUrl != null) 'imageUrl': imageUrl,
      };
}
