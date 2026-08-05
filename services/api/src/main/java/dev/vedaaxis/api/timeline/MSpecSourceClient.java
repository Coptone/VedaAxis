package dev.vedaaxis.api.timeline;

import tools.jackson.databind.JsonNode;

import java.net.URI;

interface MSpecSourceClient {
    JsonNode fetchJson(URI uri, int maxBytes);
}
