package com.katixo.hospital.common.event;

/**
 * Published by a department service (Lab / Radiology) when an order it owns changes
 * terminal status, so the CPOE {@code ClinicalOrder} that routed to it can sync.
 * Lives in {@code common} so publishers (lab/radiology) and the listener (clinical)
 * share a plain DTO without a package dependency cycle.
 *
 * @param refType e.g. {@code LAB_ORDER}, {@code RADIOLOGY_ORDER} (matches ClinicalOrder.linkedRefType)
 * @param refId   the department order id (matches ClinicalOrder.linkedRefId)
 * @param status  {@code COMPLETED} | {@code IN_PROGRESS} | {@code CANCELLED}
 */
public record DepartmentOrderStatusEvent(String tenantId, Long branchId,
                                         String refType, Long refId, String status) {}
