package ama.transcript;

/** Bounded SQL projections of an authenticated no-effect receipt reference.
 * The store pin is a protocol identifier of up to 255 scalars, not a UUID.
 * Receipt and digest lengths are fixed; splitting from the suffix also permits
 * a colon in the store identifier without exposing the LONGTEXT evidence. */
final class PublicationProofReference {
 static final String STORE="LEFT(SUBSTRING(no_effect_evidence,11,CHAR_LENGTH(no_effect_evidence)-116),255)";
 static final String RECEIPT="LEFT(RIGHT(no_effect_evidence,105),40)";
 static final String DIGEST="RIGHT(no_effect_evidence,64)";
 static final String SHAPE="CASE WHEN REGEXP_LIKE(no_effect_evidence,'^knowledge:.{1,255}:receipt-[0-9a-f]{32}:[0-9a-f]{64}$','cn') THEN 1 ELSE 0 END";
 private PublicationProofReference(){}
}
