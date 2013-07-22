package eu.ttbox.geoping.domain.smslog;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import eu.ttbox.geoping.core.AppConstants;
import eu.ttbox.geoping.domain.geotrack.GeoTrackDatabase.GeoTrackColumns;
import eu.ttbox.geoping.domain.model.SmsLog;
import eu.ttbox.geoping.domain.model.SmsLogSideEnum;
import eu.ttbox.geoping.domain.model.SmsLogTypeEnum;
import eu.ttbox.geoping.domain.smslog.SmsLogDatabase.SmsLogColumns;
import eu.ttbox.geoping.encoder.model.MessageActionEnum;
import eu.ttbox.geoping.service.encoder.SmsMessageActionEnum;
import eu.ttbox.geoping.service.encoder.SmsMessageLocEnum;
import eu.ttbox.geoping.service.encoder.adpater.BundleEncoderAdapter;
import eu.ttbox.geoping.service.encoder.params.SmsType;

public class SmsLogHelper {

    public static final String TAG = "SmsLogHelper";
    public boolean isNotInit = true;
    public int idIdx = -1;
    public int timeIdx = -1;
    public int actionIdx = -1;
    public int messageIdx = -1;
    public int messageParamIdx = -1;
    public int phoneIdx = -1;
    public int phoneMinMatchIdx = -1;
    public int smsLogTypeIdx = -1;
    public int smsLogSideIdx = -1;
    public int requestIdIdx = -1;
    public int sendAckTimeInMsIdx = -1;
    public int sendDeliveryAckTimeInMsIdx = -1;

    public static ContentValues getContentValues(SmsLog vo) {
        ContentValues initialValues = new ContentValues();
        if (vo.id > -1) {
            initialValues.put(SmsLogColumns.COL_ID, Long.valueOf(vo.id));
        }
        initialValues.put(SmsLogColumns.COL_TIME, vo.time);
        initialValues.put(SmsLogColumns.COL_PHONE, vo.phone);
        initialValues.put(SmsLogColumns.COL_ACTION, vo.action.getDbCode() );
        initialValues.put(SmsLogColumns.COL_MESSAGE, vo.message);
        initialValues.put(SmsLogColumns.COL_MESSAGE_PARAMS, vo.messageParams);
        initialValues.put(SmsLogColumns.COL_SMSLOG_TYPE, vo.smsLogType.getCode());
        initialValues.put(SmsLogColumns.COL_SMS_SIDE, vo.side.getDbCode());
        initialValues.put(SmsLogColumns.COL_REQUEST_ID, vo.requestId);

        return initialValues;
    }

    public static ContentValues getContentValues(SmsLogSideEnum side, SmsLogTypeEnum type, BundleEncoderAdapter geoMessage) {
        return getContentValues(side, type, geoMessage.getPhone(), geoMessage.getAction(), geoMessage.getMap(), null);
    }

    /**
     * Used for logging sms Message in db
     */
    public static ContentValues getContentValues(SmsLogSideEnum side, SmsLogTypeEnum type, String phone,MessageActionEnum action, Bundle params, String messageResult) {
        ContentValues values = new ContentValues();
        values.put(SmsLogColumns.COL_TIME, System.currentTimeMillis());
        values.put(SmsLogColumns.COL_PHONE, phone);
        values.put(SmsLogColumns.COL_ACTION, action.getDbCode());
        values.put(SmsLogColumns.COL_SMSLOG_TYPE, type.getCode());
        values.put(SmsLogColumns.COL_SMS_SIDE, side.getDbCode());
        values.put(SmsLogColumns.COL_MESSAGE, messageResult);

        if (params != null && !params.isEmpty()) {
            // Test Values SMS Log values
            if (params.containsKey(SmsLogColumns.COL_REQUEST_ID)) {
                String colVal = params.getString(SmsLogColumns.COL_REQUEST_ID);
                values.put(SmsLogColumns.COL_REQUEST_ID, colVal);
            }
            String paramString = convertAsJsonString(params);
            if (paramString != null) {
                values.put(SmsLogColumns.COL_MESSAGE_PARAMS, paramString);
            }
        }
        return values;
    }

    private static String convertAsJsonString(Bundle extras) {
        String result = null;
        Log.d(TAG, "convertAsJsonString : " + extras);
        try {
            JSONObject object = new JSONObject();
            for (String key : extras.keySet()) {
                String valKey = key;
                if (GeoTrackColumns.COL_LATITUDE_E6.equals(key) && extras.containsKey(GeoTrackColumns.COL_LONGITUDE_E6)) {
                    String  destKey = "WSG84";
                    double lat =  extras.getInt(GeoTrackColumns.COL_LATITUDE_E6) / AppConstants.E6 ;
                    double lng =  extras.getInt(GeoTrackColumns.COL_LONGITUDE_E6) / AppConstants.E6 ;
                    String coordString = String.format(Locale.US, "(%.6f, %.6f)", lat, lng);
                    object.put(destKey, coordString);
                } else if (GeoTrackColumns.COL_LONGITUDE_E6.equals(key)) {
                    // Ignore It, It manage before
                } else {
                    SmsMessageLocEnum fieldEnum = SmsMessageLocEnum.getByDbFieldName(key);
                    if (fieldEnum != null) {
                        valKey = fieldEnum.name();
                        switch (fieldEnum) {
                            case PERSON_ID:
                                // Ignore this Field
                                break;
                            default:
                                writeForJsonParamTypeValue(object, key, fieldEnum, extras);
                                break;
                        }
                    } else {
                        // Is a not mange field
                        Object val = extras.get(key);
                        object.put(key, val);
                    }

                }

            }

            result = object.toString();
        } catch (RuntimeException e) {
            result = e.getMessage();
        } catch (JSONException e) {
            result = e.getMessage();
        }
        return result;
    }

    private static void writeForJsonParamTypeValue(JSONObject destWrite, String key, SmsMessageLocEnum fieldEnum, Bundle extras)
            throws JSONException {
        String valKey = fieldEnum.name();
        SmsType smsType = fieldEnum.type;
        switch (smsType.wantedWriteType) {
            case GPS_PROVIDER:
            case STRING: {
                String val = extras.getString(key);
                destWrite.put(valKey, val);
            }
            break;
            case INT: {
                int val = extras.getInt(key);
                destWrite.put(valKey, val);
            }
            break;
            case DATE: {
                long dateAsLong = extras.getLong(key);
                String val = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", dateAsLong);
                destWrite.put(valKey, val);
            }
            break;
            case LONG: {
                Long val = Long.valueOf(extras.getLong(key));
                destWrite.put(valKey, val);
            }
            break;

            default:
                Log.d(TAG, "Ignore SmsMessageLocEnum." + fieldEnum + " for type of " + fieldEnum.type);
                break;
        }
    }

    // ===========================================================
    // Data Accessor
    // ===========================================================

    public SmsLogHelper initWrapper(Cursor cursor) {
        idIdx = cursor.getColumnIndex(SmsLogColumns.COL_ID);
        timeIdx = cursor.getColumnIndex(SmsLogColumns.COL_TIME);
        actionIdx = cursor.getColumnIndex(SmsLogColumns.COL_ACTION);
        phoneIdx = cursor.getColumnIndex(SmsLogColumns.COL_PHONE);
        phoneMinMatchIdx = cursor.getColumnIndex(SmsLogColumns.COL_PHONE_MIN_MATCH);
        smsLogTypeIdx = cursor.getColumnIndex(SmsLogColumns.COL_SMSLOG_TYPE);
        messageIdx = cursor.getColumnIndex(SmsLogColumns.COL_MESSAGE);
        messageParamIdx = cursor.getColumnIndex(SmsLogColumns.COL_MESSAGE_PARAMS);
        smsLogSideIdx = cursor.getColumnIndex(SmsLogColumns.COL_SMS_SIDE);
        requestIdIdx = cursor.getColumnIndex(SmsLogColumns.COL_REQUEST_ID);

        sendAckTimeInMsIdx = cursor.getColumnIndex(SmsLogColumns.COL_MSG_ACK_SEND_TIME_MS);
        sendDeliveryAckTimeInMsIdx = cursor.getColumnIndex(SmsLogColumns.COL_MSG_ACK_DELIVERY_TIME_MS);

        isNotInit = false;
        return this;
    }

    public SmsLog getEntity(Cursor cursor) {
        if (isNotInit) {
            initWrapper(cursor);
        }
        SmsLog user = new SmsLog();
        user.setId(idIdx > -1 ? cursor.getLong(idIdx) : -1);
        user.setTime(timeIdx > -1 ? cursor.getLong(timeIdx) : SmsLog.UNSET_TIME);
        user.setAction(actionIdx > -1 ? getSmsMessageActionEnum(cursor) : null);
        user.setPhone(phoneIdx > -1 ? cursor.getString(phoneIdx) : null);
        user.setSmsLogType(smsLogTypeIdx > -1 ? getSmsLogType(cursor) : null);
        user.setMessage(messageIdx > -1 ? cursor.getString(messageIdx) : null);
        user.setMessageParams(messageParamIdx > -1 ? cursor.getString(messageParamIdx) : null);
        user.setSide(smsLogSideIdx > -1 ? getSmsLogSideEnum(cursor) : null);
        user.setRequestId(requestIdIdx > -1 ? cursor.getString(requestIdIdx) : null);
        return user;
    }

    private SmsLogHelper setTextWithIdx(TextView view, Cursor cursor, int idx) {
        view.setText(cursor.getString(idx));
        return this;
    }

    public SmsLogHelper setTextSmsLogId(TextView view, Cursor cursor) {
        return setTextWithIdx(view, cursor, idIdx);
    }

    public String getSmsLogIdAsString(Cursor cursor) {
        return cursor.getString(idIdx);
    }

    public long getSmsLogId(Cursor cursor) {
        return cursor.getLong(idIdx);
    }

    public String getSmsLogPhone(Cursor cursor) {
        return cursor.getString(phoneIdx);
    }

    public SmsLogTypeEnum getSmsLogType(Cursor cursor) {
        return SmsLogTypeEnum.getByCode(cursor.getInt(smsLogTypeIdx));
    }

    public MessageActionEnum getSmsMessageActionEnum(Cursor cursor) {
        String actionValue = cursor.getString(actionIdx);
        return MessageActionEnum.getByDbCode(actionValue);
    }

    public String getSmsMessageActionString(Cursor cursor) {
        String actionValue = cursor.getString(actionIdx);
        return actionValue;
    }

    // ===========================================================
    // Field Setter
    // ===========================================================

    public SmsLogSideEnum getSmsLogSideEnum(Cursor cursor) {
        int key = cursor.getInt(smsLogSideIdx);
        return SmsLogSideEnum.getByDbCode(key);
    }

    public long getSendAckTimeInMs(Cursor cursor) {
        return cursor.getLong(sendAckTimeInMsIdx);
    }

    public long getSendDeliveryAckTimeInMs(Cursor cursor) {
        return cursor.getLong(sendDeliveryAckTimeInMsIdx);
    }

    public String getMessage(Cursor cursor) {
        return cursor.getString(messageIdx);
    }

    //

    public String getMessageParams(Cursor cursor) {
        return cursor.getString(messageParamIdx);
    }

    public SmsLogHelper setTextSmsLogAction(TextView view, Cursor cursor) {
        return setTextWithIdx(view, cursor, actionIdx);
    }

    public SmsLogHelper setTextSmsLogMessage(TextView view, Cursor cursor) {
        return setTextWithIdx(view, cursor, messageIdx);
    }

    public SmsLogHelper setTextSmsLogPhone(TextView view, Cursor cursor) {
        return setTextWithIdx(view, cursor, phoneIdx);
    }

    public SmsLogHelper setTextSmsLogTime(TextView view, Cursor cursor) {
        return setTextWithIdx(view, cursor, timeIdx);
    }

    public long getSmsLogTime(Cursor cursor) {
        return cursor.getLong(timeIdx);
    }


}
