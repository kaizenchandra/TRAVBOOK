package com.travbook.shared.domain;
public record SupplierCoverage(String supplier,Status status){public enum Status{AVAILABLE,UNAVAILABLE}}
