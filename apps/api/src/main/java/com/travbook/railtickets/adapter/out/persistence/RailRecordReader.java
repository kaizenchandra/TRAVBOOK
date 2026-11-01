package com.travbook.railtickets.adapter.out.persistence;
import com.travbook.railtickets.domain.model.RailTicket.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
/** Explicit additive persistence compatibility; HTTP request validation stays strict. */
public final class RailRecordReader {
 private final ObjectMapper json;
 public RailRecordReader(ObjectMapper json){this.json=json;}
 public Booking booking(String value){var node=(ObjectNode)json.readTree(value);if(!node.has("cancelledPassengerIndexes"))node.putNull("cancelledPassengerIndexes");if(node.get("cancellation") instanceof ObjectNode cancellation&&cancellation.get("quote") instanceof ObjectNode quote&&!quote.has("passengerIndexes"))quote.putNull("passengerIndexes");return json.treeToValue(node,Booking.class);}
 public CancellationQuote cancellation(String value){var node=(ObjectNode)json.readTree(value);if(!node.has("passengerIndexes"))node.putNull("passengerIndexes");return json.treeToValue(node,CancellationQuote.class);}
 public Voucher voucher(String value){var node=(ObjectNode)json.readTree(value);if(!node.has("revision"))node.put("revision",0);return json.treeToValue(node,Voucher.class);}
}
