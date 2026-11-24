package com.fraudshield.siteintel;

/** One explainable piece of a link's score: points, stable reason code (10.9) and the sentence the user sees. */
public record Finding(int points, String code, String reason) {}
