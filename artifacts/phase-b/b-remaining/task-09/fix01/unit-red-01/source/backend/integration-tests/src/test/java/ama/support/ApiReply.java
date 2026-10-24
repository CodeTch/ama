package ama.support;

import com.fasterxml.jackson.databind.JsonNode;
public record ApiReply(int status, JsonNode body) {}
