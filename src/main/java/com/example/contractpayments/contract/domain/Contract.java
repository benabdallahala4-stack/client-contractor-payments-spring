package com.example.contractpayments.contract.domain;

import java.util.UUID;

public record Contract(UUID id, UUID clientId, UUID contractorId, String title, String status) { }
