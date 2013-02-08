package eu.ttbox.geoping.service.slave.receiver;

import java.util.ArrayList;

import eu.ttbox.geoping.domain.pairing.PairingDatabase.PairingColumns;
import eu.ttbox.geoping.service.encoder.params.SmsValueEventTypeEnum;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneCallReceiver extends BroadcastReceiver {

    private static final String TAG = "PhoneCallReceiver";

    // Prefs
    public static final String PHONE_RECEIVER_PREFS_NAME = "eu.ttbox.geoping.prefs.PhoneCallReceiver";

    // Action
    private static final String ACTION_PHONE_STATE_CHANGED = TelephonyManager.ACTION_PHONE_STATE_CHANGED;
    private static final String ACTION_NEW_OUTGOING_CALL = "android.intent.action.NEW_OUTGOING_CALL";

    // Extras Incoming
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_INCOMING_NUMBER = "incoming_number";

    // Extras Outgoing
    private static final String EXTRA_OUTGOING_NUMBER = "android.intent.extra.PHONE_NUMBER";

    // Incomming State
    private static final String STATE_RINGING = "RINGING"; // Sonne
    private static final String STATE_OFFHOOK = "OFFHOOK"; // Decrocher
    private static final String STATE_IDLE = "IDLE"; // Racroche

    // Prefs
    private static final String PREFS_KEY_PHONE_NUMBER = "PREFS_KEY_PHONE_NUMBER";
    private static final String PREFS_KEY_ACTION = "PREFS_KEY_ACTION";
    private static final String PREFS_KEY_INLINE_TIME_IN_MS = "PREFS_KEY_INLINE_TIME_IN_MS";

    private static final int PREFS_ACTION_INCOMING = 1;
    private static final int PREFS_ACTION_OUTGOING = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_PHONE_STATE_CHANGED.equals(action)) {
            Log.d(TAG, "PhoneState action : " + action);
            Bundle extras = intent.getExtras();
            printExtras(extras);
            String state = extras.getString(EXTRA_STATE);
            SharedPreferences prefs = context.getSharedPreferences(PHONE_RECEIVER_PREFS_NAME, Context.MODE_PRIVATE);
            if (STATE_RINGING.equals(state)) {
                String phoneNumber = extras.getString(EXTRA_INCOMING_NUMBER);
                Log.d(TAG, "PhoneState incomming call : " + phoneNumber);
                // Prefs
                SharedPreferences.Editor prefEditor = prefs.edit();
                prefEditor.putString(PREFS_KEY_PHONE_NUMBER, phoneNumber);
                prefEditor.putInt(PREFS_KEY_ACTION, PREFS_ACTION_INCOMING);
                prefEditor.commit();

            } else if (STATE_OFFHOOK.equals(state)) {
                // Decrocher
                SharedPreferences.Editor prefEditor = prefs.edit();
                long now = System.currentTimeMillis();
                prefEditor.putLong(PREFS_KEY_INLINE_TIME_IN_MS, now);
                prefEditor.commit();
                
            } else if (STATE_IDLE.equals(state)) {
                // Racroche ou ignore
                long endCall = System.currentTimeMillis();
                long beginCall = prefs.getLong(PREFS_KEY_INLINE_TIME_IN_MS, -1);
                int callAction = prefs.getInt(PREFS_KEY_ACTION, -1);
                String phoneNumber = prefs.getString(PREFS_KEY_PHONE_NUMBER, null);
                // Clear Values
                SharedPreferences.Editor prefEditor = prefs.edit();
                prefEditor.remove(PREFS_KEY_PHONE_NUMBER);
                prefEditor.remove(PREFS_KEY_ACTION);
                prefEditor.remove(PREFS_KEY_INLINE_TIME_IN_MS);
                prefEditor.commit();
                // Manage Datas
                String message =  manageCallDatas(context, phoneNumber, callAction, beginCall, endCall);
            }
        } else if (ACTION_NEW_OUTGOING_CALL.equals(action)) {
            Log.d(TAG, "PhoneState action : " + action);
            Bundle extras = intent.getExtras();
            printExtras(extras);
            // String
            String composePhoneNumber = extras.getString(EXTRA_OUTGOING_NUMBER);
            Log.d(TAG, "PhoneState compose PhoneNumber : " + composePhoneNumber);
            // Service
            SharedPreferences prefs = context.getSharedPreferences(PHONE_RECEIVER_PREFS_NAME, Context.MODE_PRIVATE);
            // Prefs
            SharedPreferences.Editor prefEditor = prefs.edit();
            prefEditor.putString(PREFS_KEY_PHONE_NUMBER, composePhoneNumber);
            prefEditor.putInt(PREFS_KEY_ACTION, PREFS_ACTION_OUTGOING);
            prefEditor.commit();
        }
    }

    private String manageCallDatas(Context context, String phoneNumber, int callAction, long beginCall, long endCall) {
        String message = null;
        if (beginCall < 0) {
            // Pas de communication
            switch (callAction) {
            case PREFS_ACTION_OUTGOING:
                message = "A essayer d'appeler le numéro " + phoneNumber;
                break;
            case PREFS_ACTION_INCOMING:
                message = "A recu un appel non repondu du numéro " + phoneNumber;
                break;
            default:
                break;
            }
        } else {
            int callDurationInS = (int) ((endCall - beginCall) / 1000);
            switch (callAction) {
            case PREFS_ACTION_OUTGOING:
                message = "A appeler " + callDurationInS + " s le numéro " + phoneNumber;
                break;
            case PREFS_ACTION_INCOMING:
                message = "A Recu un appel de " + callDurationInS + " s du numéro " + phoneNumber;
                break;
            default:
                break;
            }
        }
        Log.d(TAG, "PhoneState result : " + message);
        // TODO
//        ArrayList<String> phones= SpyNotificationHelper.searchListPhonesForNotif(context, PairingColumns.COL_NOTIF_PHONE_CALL);
//        if (phones != null) {
//            // Send Sms
//            SpyNotificationHelper.sendEventSpySmsMessage(context,phones,  SmsValueEventTypeEnum.PHONE_CALL);
//        }
        return message;
    }

    private void printExtras(Bundle extras) {
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            Log.d(TAG, "PhoneState extras : " + key + " = " + value);
        }
    }

}
