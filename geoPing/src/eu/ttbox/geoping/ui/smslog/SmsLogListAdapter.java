package eu.ttbox.geoping.ui.smslog;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import eu.ttbox.geoping.GeoPingApplication;
import eu.ttbox.geoping.R;
import eu.ttbox.geoping.domain.model.SmsLogSideEnum;
import eu.ttbox.geoping.domain.model.SmsLogTypeEnum;
import eu.ttbox.geoping.domain.smslog.SmsLogHelper;
import eu.ttbox.geoping.service.core.ContactHelper;
import eu.ttbox.geoping.service.encoder.SmsMessageActionEnum;
import eu.ttbox.geoping.ui.person.PhotoThumbmailCache;

/**
 * <ul>
 * <li>platform_packages_apps_contacts/res/layout/call_log_list_item.xml</li>
 * <li> platform_packages_apps_contacts/res/layout/call_detail_history_item.xml</li>
 * <li>com.android.contacts.PhoneCallDetailsHelper#callTypeAndDate</li>
 * <li> android.text.format.DateUtils#getRelativeTimeSpanString(long time, long now, long minResolution,  int flags)</li>
 *</ul>
 */
public class SmsLogListAdapter extends android.support.v4.widget.ResourceCursorAdapter {

    private static final String TAG = "SmsLogListAdapter";

	private SmsLogHelper helper;

    private boolean isNotBinding = true;

    private SmsLogResources mResources;

    private android.content.res.Resources resources;

    private boolean isDisplayContactDetail = true;
    private boolean isDisplayPhoneAndName = true;
    // Cache
    private PhotoThumbmailCache photoCache;
    private PersonNameFinderHelper cacheNameFinder;

    // ===========================================================
    // Constructor
    // ===========================================================

    public SmsLogListAdapter(Context context, Cursor c, int flags, boolean isDisplayContactDetail ) {
        super(context, R.layout.smslog_list_item, c, flags);
        this.resources = context.getResources();
        this.mResources = new SmsLogResources(context);
        this.isDisplayContactDetail = isDisplayContactDetail;
        // Cache
        this.photoCache = ((GeoPingApplication) context.getApplicationContext()).getPhotoThumbmailCache();
        this.cacheNameFinder = new PersonNameFinderHelper(mContext, isDisplayPhoneAndName);
    }

    private void intViewBinding(View view, Context context, Cursor cursor) {
        // Init Cursor
        helper = new SmsLogHelper().initWrapper(cursor);
        isNotBinding = false;
    }

    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        if (isNotBinding) {
            intViewBinding(view, context, cursor);
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        // Bind Value
        SmsLogTypeEnum smLogType = helper.getSmsLogType(cursor);
        Drawable iconType = mResources.getCallTypeDrawable(smLogType);
        holder.smsType.setImageDrawable(iconType);
        // Text
        SmsMessageActionEnum action = helper.getSmsMessageActionEnum(cursor);
        String actionLabel;
        if (action!=null) {
        	  actionLabel = getSmsActionLabel(action); 
        } else {
        	 actionLabel = helper.getSmsMessageActionString(cursor);
        }
        holder.actionText.setText(actionLabel);
        // Phone
        String phone = helper.getSmsLogPhone(cursor);
        if (isDisplayContactDetail) {
            SmsLogSideEnum side = helper.getSmsLogSideEnum(cursor);
            cacheNameFinder.setTextViewPersonNameByPhone(holder.phoneText, phone, side);
            holder.phoneText.setVisibility(View.VISIBLE);
        } else {
            holder.phoneText.setVisibility(View.GONE);
        }
        // Time
        long time = helper.getSmsLogTime(cursor);
        String timeFormat = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", time);
        holder.timeText.setText(timeFormat);
        // Time relative
        long currentTimeMillis = System.currentTimeMillis();
        CharSequence dateText =
                DateUtils.getRelativeTimeSpanString(time,
                        currentTimeMillis,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
        holder.timeAgoText.setText(dateText);
        // Load Photos
        if (isDisplayContactDetail) {
           loadPhoto(holder, null, phone);
            holder.photoImageView.setVisibility(View.VISIBLE);
        } else {
            holder.photoImageView.setVisibility(View.GONE);
        }
    }

  


    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        // Then populate the ViewHolder
        ViewHolder holder = new ViewHolder();
        holder.timeAgoText = (TextView) view.findViewById(R.id.smslog_list_item_time_ago);
        holder.timeText = (TextView) view.findViewById(R.id.smslog_list_item_time);
        holder.phoneText = (TextView) view.findViewById(R.id.smslog_list_item_phone);
        holder.actionText = (TextView) view.findViewById(R.id.smslog_list_item_action);
        holder.smsType = (ImageView) view.findViewById(R.id.smslog_list_item_smsType_imgs);
        holder.photoImageView = (ImageView) view.findViewById(R.id.smslog_photo_imageView);
        // and store it inside the layout.
        view.setTag(holder);
        return view;
    }

    static class ViewHolder {
        TextView actionText;
        TextView timeText;
        TextView timeAgoText;
        TextView phoneText;
        ImageView smsType;
        ImageView photoImageView;
    }



    
    private String getSmsActionLabel(SmsMessageActionEnum action) {
//    	Log.d(TAG, "getSmsActionLabel : "  + action); 
    	 return action.getLabel(resources);
    	 	
    	// FIXME
//        switch (action) {
//        case GEOPING_REQUEST:
//            return mResources.actionGeoPingRequest;
//        case ACTION_GEO_LOC:
//            return mResources.actionGeoPingResponse;
//        case ACTION_GEO_PAIRING:
//            return mResources.actionPairingRequest;
//        case ACTION_GEO_PAIRING_RESPONSE:
//            return mResources.actionPairingResponse;
//         default:
//            return action.name();
//        }
    }


    // ===========================================================
    // Photo Loader
    // ===========================================================

    /**
     * Pour plus de details sur l'intégration dans les contacts consulter
     * <ul>
     * <li>item_photo_editor.xml</li>
     * <li>com.android.contacts.editor.PhotoEditorView</li>
     * <li>com.android.contacts.detail.PhotoSelectionHandler</li>
     * <li>com.android.contacts.editor.ContactEditorFragment.PhotoHandler</li>
     * </ul>
     *
     * @param contactId
     */
    private void loadPhoto(ViewHolder holder, String contactId, final String phone) {
        Bitmap photo = null;
        boolean isContactId = !TextUtils.isEmpty(contactId);
        boolean isContactPhone = !TextUtils.isEmpty(phone);
        // Search in cache
        if (photo == null && isContactId) {
            photo = photoCache.get(contactId);
        }
        if (photo == null && isContactPhone) {
            photo = photoCache.get(phone);
        }
        // Set Photo
        if (photo != null) {
            holder.photoImageView.setImageBitmap(photo);
        } else if (isContactId || isContactPhone) {
            // Cancel previous Async
            final PhotoLoaderAsyncTask oldTask = (PhotoLoaderAsyncTask) holder.photoImageView.getTag();
            if (oldTask != null) {
                oldTask.cancel(false);
            }
            // Load photos
            PhotoLoaderAsyncTask newTask = new PhotoLoaderAsyncTask(holder.photoImageView);
            holder.photoImageView.setTag(newTask);
            newTask.execute(contactId, phone);
        }
        // photoImageView.setEditorListener(new EditorListener() {
        //
        // @Override
        // public void onRequest(int request) {
        // Toast.makeText(getActivity(), "Click to phone " + phone,
        // Toast.LENGTH_SHORT).show();
        // }
        //
        // });
    }

    public class PhotoLoaderAsyncTask extends AsyncTask<String, Void, Bitmap> {

        final ImageView holder;

        public PhotoLoaderAsyncTask(ImageView holder) {
            super();
            this.holder = holder;
        }

        @Override
        protected void onPreExecute() {
            holder.setTag(this);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            String contactIdSearch = params[0];
            String phoneSearch = null;
            if (params.length > 1) {
                phoneSearch = params[1];
            }
            Bitmap result = ContactHelper.openPhotoBitmap(mContext, photoCache, contactIdSearch, phoneSearch);
            Log.d(TAG, "PhotoLoaderAsyncTask load photo : " + (result != null));
            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (holder.getTag() == this) {
                holder.setImageBitmap(result);
                holder.setTag(null);
                Log.d(TAG, "PhotoLoaderAsyncTask onPostExecute photo : " + (result != null));
            }
        }
    }

    // ===========================================================
    // Others
    // ===========================================================

}
