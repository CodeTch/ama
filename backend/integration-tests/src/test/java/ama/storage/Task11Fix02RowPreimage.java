package ama.storage;
import java.util.*;import java.time.*;import java.math.*;
/** Explicit typed JDBC cells, sorted column names; caller supplies actual primary-key row order. */
final class Task11Fix02RowPreimage {
 static String hash(List<? extends Map<String,?>> rows)throws Exception{var frozen=new ArrayList<Map<String,List<String>>>();for(var row:rows){var cells=new TreeMap<String,List<String>>();row.forEach((key,value)->cells.put(key,cell(value)));frozen.add(Collections.unmodifiableMap(cells));}byte[] bytes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(frozen);return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
 static List<String> cell(Object value){if(value==null)return List.of("NULL");String text;
 if(value instanceof byte[] data)text=Base64.getEncoder().encodeToString(data);
 else if(value instanceof java.sql.Timestamp stamp)text=stamp.toLocalDateTime().toString();
 else if(value instanceof java.sql.Date date)text=date.toLocalDate().toString();
 else if(value instanceof java.sql.Time time)text=time.toLocalTime().toString();
 else if(value instanceof java.util.Date date)text=Long.toString(date.getTime());
 else if(value instanceof String||value instanceof Boolean||value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof BigInteger||value instanceof BigDecimal||value instanceof Float||value instanceof Double||value instanceof LocalDateTime||value instanceof LocalDate||value instanceof LocalTime||value instanceof OffsetDateTime||value instanceof OffsetTime||value instanceof Instant)text=value.toString();
 else throw new IllegalArgumentException("Unsupported JDBC cell type "+value.getClass().getName());
 return List.of(value.getClass().getName(),text);
 }
}
