/// Lab DTOs — mirror LabController on the backend.

class LabTest {
  const LabTest({
    required this.id,
    required this.testCode,
    required this.testName,
    required this.specimenType,
    required this.rate,
    this.unit,
    this.referenceRange,
  });

  final int id;
  final String testCode;
  final String testName;
  final String specimenType;
  final num rate;
  final String? unit;
  final String? referenceRange;

  factory LabTest.fromJson(Map<String, dynamic> json) {
    return LabTest(
      id: json['id'] as int,
      testCode: json['testCode'] as String,
      testName: json['testName'] as String,
      specimenType: json['specimenType'] as String,
      rate: json['rate'] as num,
      unit: json['unit'] as String?,
      referenceRange: json['referenceRange'] as String?,
    );
  }
}

class CreateOrderRequest {
  const CreateOrderRequest({
    required this.sourceType,
    required this.sourceId,
    required this.testCodes,
    this.notes,
  });

  final String sourceType;
  final int sourceId;
  final List<String> testCodes;
  final String? notes;

  Map<String, dynamic> toJson() => {
        'sourceType': sourceType,
        'sourceId': sourceId,
        'testCodes': testCodes,
        if (notes != null) 'notes': notes,
      };
}

class LabOrderView {
  const LabOrderView({
    required this.id,
    required this.orderNumber,
    required this.sourceType,
    required this.sourceId,
    required this.orderStatus,
    required this.createdAt,
    this.notes,
    required this.items,
  });

  final int id;
  final String orderNumber;
  final String sourceType;
  final int sourceId;
  final String orderStatus;
  final String createdAt;
  final String? notes;
  final List<LabOrderItem> items;

  factory LabOrderView.fromJson(Map<String, dynamic> json) {
    return LabOrderView(
      id: json['id'] as int,
      orderNumber: json['orderNumber'] as String,
      sourceType: json['sourceType'] as String,
      sourceId: json['sourceId'] as int,
      orderStatus: json['orderStatus'] as String,
      createdAt: json['createdAt'] as String,
      notes: json['notes'] as String?,
      items: (json['items'] as List?)
              ?.map((e) => LabOrderItem.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class LabOrderItem {
  const LabOrderItem({
    required this.itemId,
    required this.testCode,
    required this.testName,
    required this.specimenType,
    required this.itemStatus,
    this.sampleId,
    this.barcode,
    this.resultValue,
    this.isAbnormal,
    this.reportStatus,
    this.collectedAt,
  });

  final int itemId;
  final String testCode;
  final String testName;
  final String specimenType;
  final String itemStatus;
  final int? sampleId;
  final String? barcode;
  final String? resultValue;
  final bool? isAbnormal;
  final String? reportStatus;
  final String? collectedAt;

  factory LabOrderItem.fromJson(Map<String, dynamic> json) {
    return LabOrderItem(
      itemId: json['itemId'] as int,
      testCode: json['testCode'] as String,
      testName: json['testName'] as String,
      specimenType: json['specimenType'] as String,
      itemStatus: json['itemStatus'] as String,
      sampleId: json['sampleId'] as int?,
      barcode: json['barcode'] as String?,
      resultValue: json['resultValue'] as String?,
      isAbnormal: json['isAbnormal'] as bool?,
      reportStatus: json['reportStatus'] as String?,
      collectedAt: json['collectedAt'] as String?,
    );
  }
}

class EnterResultRequest {
  const EnterResultRequest({
    required this.resultValue,
    this.isAbnormal,
    this.fileUrl,
  });

  final String resultValue;
  final bool? isAbnormal;
  final String? fileUrl;

  Map<String, dynamic> toJson() => {
        'resultValue': resultValue,
        if (isAbnormal != null) 'isAbnormal': isAbnormal,
        if (fileUrl != null) 'fileUrl': fileUrl,
      };
}
