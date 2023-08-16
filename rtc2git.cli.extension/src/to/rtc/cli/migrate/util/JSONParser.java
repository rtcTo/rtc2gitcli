package to.rtc.cli.migrate.util;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.eclipse.e4.core.services.util.JSONObject;

public class JSONParser {

	private static final Field JSONObjectMapField;

	static {
		final Class<JSONObject> jsonObjectClass = JSONObject.class;
		Field mapField;
		try {
			mapField = jsonObjectClass.getDeclaredField("map");
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("Internal coding error", e);
		} catch (SecurityException e) {
			throw new IllegalStateException("Internal coding error", e);
		}
		mapField.setAccessible(true);
		JSONObjectMapField = mapField;
	}

	/**
	 * {@link JSONObject} has a defect where, while it can parse JSON perfectly,
	 * it's unable to let callers access anything that isn't a String. e.g. we can't
	 * get a numerical field out of it as it throws a {@link ClassCastException}
	 * trying to turn a {@link BigDecimal} into something else. So, if we need to
	 * read a number, we need to access the data using this nasty method instead.
	 * 
	 * @param whatFrom The JSONObject we are reading data from.
	 * @return The underlying Map of data, indexed by field name.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getMap(JSONObject whatFrom) {
		try {
			final Object object = JSONParser.JSONObjectMapField.get(whatFrom);
			return (Map<String, Object>) object;
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Internal coding error", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Internal coding error", e);
		}
	}

	/**
	 * {@link JSONObject} has a defect where, while it can parse JSON perfectly,
	 * it's unable to let callers access numerical values.
	 * 
	 * @param jsonObjectMap The value from {@link #getMap(JSONObject)}.
	 * @param fieldName     The name of the numerical field to read.
	 * @return The numerical value.
	 */
	public static long getLong(Map<String, Object> jsonObjectMap, String fieldName) {
		final Object fieldValue = jsonObjectMap.get(fieldName);
		final BigDecimal fieldValueAsBigDecimal = (BigDecimal) fieldValue;
		final long workitemNumber = fieldValueAsBigDecimal.longValueExact();
		return workitemNumber;
	}

	/**
	 * Parses the JSON output of a CLI command.
	 * 
	 * @param toBeParsed The lines that were output by the command.
	 * @return A {@link JSONObject} containing the parsed output.
	 */
	public static JSONObject parseJSON(final List<String> toBeParsed) {
		final StringBuilder sb = new StringBuilder();
		for (final String s : toBeParsed) {
			sb.append(s).append('\n');
		}
		final JSONObject parsedJson = JSONObject.deserialize(sb.toString());
		return parsedJson;
	}

	/**
	 * Same as {@link JSONObject#getObjects(String)}, except this returns null if
	 * the field isn't present.
	 * 
	 * @param json      The {@link JSONObject} being examined.
	 * @param fieldName The field that might not be present.
	 * @return Same as {@link JSONObject#getObjects(String)}, or null if the field
	 *         is not present.
	 */
	public static JSONObject[] getObjectsOrNull(JSONObject json, String fieldName) {
		try {
			return json.getObjects(fieldName);
		} catch (NullPointerException e) {
			return null;
		}
	}

	/**
	 * Same as {@link JSONObject#getObjects(String)}, except this returns an empty
	 * array if the field isn't present.
	 * 
	 * @param json      The {@link JSONObject} being examined.
	 * @param fieldName The field that might not be present.
	 * @return Same as {@link JSONObject#getObjects(String)}, or an empty array if
	 *         the field is not present.
	 */
	public static JSONObject[] getObjectsOrEmpty(JSONObject json, String fieldName) {
		final JSONObject[] objectsOrNull = getObjectsOrNull(json, fieldName);
		if (objectsOrNull == null) {
			return new JSONObject[0];
		}
		return objectsOrNull;
	}
}
