package com.criteo.publisher.network;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.criteo.publisher.BuildConfig;
import com.criteo.publisher.Util.DeviceUtil;
import com.criteo.publisher.Util.LoggingUtil;
import com.criteo.publisher.Util.NetworkResponseListener;
import com.criteo.publisher.Util.UserPrivacyUtil;
import com.criteo.publisher.model.CacheAdUnit;
import com.criteo.publisher.model.Cdb;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.Publisher;
import com.criteo.publisher.model.Slot;
import com.criteo.publisher.model.User;
import java.util.Hashtable;
import java.util.List;
import org.json.JSONObject;

public class CdbDownloadTask extends AsyncTask<Object, Void, NetworkResult> {

  private static final String TAG = "Criteo.CDT";

  private final boolean isConfigRequested;
  private boolean isCdbRequested;
  private final String userAgent;
  private final NetworkResponseListener responseListener;
  private final List<CacheAdUnit> cacheAdUnits;
  private final PubSdkApi api;
  private final DeviceUtil deviceUtil;
  private final LoggingUtil loggingUtil;
  private final Hashtable<CacheAdUnit, CdbDownloadTask> bidsInCdbTask;
  private final UserPrivacyUtil userPrivacyUtil;

  public CdbDownloadTask(
      NetworkResponseListener responseListener,
      boolean isConfigRequested,
      boolean isCdbRequested,
      String userAgent,
      List<CacheAdUnit> adUnits,
      Hashtable<CacheAdUnit, CdbDownloadTask> bidsInMap,
      DeviceUtil deviceUtil,
      LoggingUtil loggingUtil,
      UserPrivacyUtil userPrivacyUtil,
      PubSdkApi api
  ) {
    this.responseListener = responseListener;
    this.isConfigRequested = isConfigRequested;
    this.isCdbRequested = isCdbRequested;
    this.userAgent = userAgent;
    this.cacheAdUnits = adUnits;
    this.bidsInCdbTask = bidsInMap;
    this.deviceUtil = deviceUtil;
    this.loggingUtil = loggingUtil;
    this.api = api;
    this.userPrivacyUtil = userPrivacyUtil;
  }

  @Override
  protected NetworkResult doInBackground(Object... objects) {
    NetworkResult result = null;

    try {
      result = doCdbDownloadTask(objects);
    } catch (Throwable tr) {
      Log.e(TAG, "Internal CDT exec error.", tr);
    }

    return result;
  }

  private NetworkResult doCdbDownloadTask(Object[] objects) {
    if (objects.length < 3) {
      return null;
    }
    int profile = (Integer) objects[0];
    User user = (User) objects[1];
    Publisher publisher = (Publisher) objects[2];
    if (profile <= 0) {
      return null;
    }

    JSONObject configResult = requestConfig(user, publisher);
    Cdb cdbResult = requestCdb(profile, user, publisher);
    return new NetworkResult(cdbResult, configResult);
  }

  @Nullable
  private JSONObject requestConfig(User user, Publisher publisher) {
    if (!isConfigRequested) {
      return null;
    }

    JSONObject configResult = api.loadConfig(
        publisher.getCriteoPublisherId(),
        publisher.getBundleId(),
        user.getSdkVer());

    // FIXME EE-792 Proper solution would be to separate the remote config and the CDB calls
    //  This will remove the is*Requested boolean, this ugly partial parsing and partial
    //  result. NetworkResult would then have no meaning. Also, extracting the remote config
    //  call would let us organize properly the calls at startup (config, CDB, GUM).
    Boolean isKillSwitchEnabled = Config.parseKillSwitch(configResult);
    if (isKillSwitchEnabled != null && isKillSwitchEnabled) {
      isCdbRequested = false;
    }

    return configResult;
  }

  @Nullable
  private Cdb requestCdb(int profile, User user, Publisher publisher) {
    if (!isCdbRequested) {
      return null;
    }

    Cdb cdbRequest = buildCdbRequest(profile, user, publisher);
    Cdb cdbResult = api.loadCdb(cdbRequest, userAgent);
    logCdbResponse(cdbResult);

    return cdbResult;
  }

  @NonNull
  private Cdb buildCdbRequest(int profile, User user, Publisher publisher) {
    String advertisingId = deviceUtil.getAdvertisingId();
    if (!TextUtils.isEmpty(advertisingId)) {
      user.setDeviceId(advertisingId);
    }

    String uspIab = userPrivacyUtil.getIabUsPrivacyString();
    if (uspIab != null && !uspIab.isEmpty()) {
      user.setUspIab(uspIab);
    }

    String uspOptout = userPrivacyUtil.getUsPrivacyOptout();
        if (uspOptout != null && !uspOptout.isEmpty()) {
            user.setUspOptout(uspOptout);
        }Cdb cdbRequest = new Cdb();
    cdbRequest.setRequestedAdUnits(cacheAdUnits);
    cdbRequest.setUser(user);
    cdbRequest.setPublisher(publisher);
    cdbRequest.setSdkVersion(BuildConfig.VERSION_NAME);
    cdbRequest.setProfileId(profile);
    JSONObject gdpr = userPrivacyUtil.gdpr();
    if (gdpr != null) {
      cdbRequest.setGdprConsent(gdpr);
    }
    return cdbRequest;
  }

  private void logCdbResponse(Cdb response) {
    if (loggingUtil.isLoggingEnabled() && response != null && response.getSlots().size() > 0) {
      StringBuilder builder = new StringBuilder();
      for (Slot slot : response.getSlots()) {
        builder.append(slot.toString());
        builder.append("\n");
      }
      Log.d(TAG, builder.toString());
    }
  }

  @Override
  protected void onPostExecute(NetworkResult networkResult) {
    try {
      doOnPostExecute(networkResult);
    } catch (Throwable tr) {
      Log.e(TAG, "Internal CDT PostExec error.", tr);
    }
  }

  private void doOnPostExecute(NetworkResult networkResult) {
    super.onPostExecute(networkResult);

    for (CacheAdUnit cacheAdUnit : cacheAdUnits) {
      bidsInCdbTask.remove(cacheAdUnit);
    }

    if (responseListener != null && networkResult != null) {
      if (networkResult.getCdb() != null) {
        responseListener.setCacheAdUnits(networkResult.getCdb().getSlots());
        responseListener.setTimeToNextCall(networkResult.getCdb().getTimeToNextCall());
      }
      if (networkResult.getConfig() != null) {
        responseListener.refreshConfig(networkResult.getConfig());
      }
    }
  }
}
