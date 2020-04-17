package com.criteo.publisher;

import static com.criteo.publisher.CriteoListenerCode.CLICK;
import static com.criteo.publisher.CriteoListenerCode.CLOSE;
import static com.criteo.publisher.CriteoListenerCode.INVALID;
import static com.criteo.publisher.CriteoListenerCode.VALID;

import android.content.ComponentName;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.webkit.WebViewClient;
import com.criteo.publisher.activity.TopActivityFinder;
import com.criteo.publisher.adview.AdWebViewClient;
import com.criteo.publisher.adview.AdWebViewListener;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.Slot;
import com.criteo.publisher.model.TokenValue;
import com.criteo.publisher.tasks.CriteoBannerListenerCallTask;
import com.criteo.publisher.tasks.CriteoBannerLoadTask;
import com.criteo.publisher.util.AdUnitType;
import com.criteo.publisher.util.RunOnUiThreadExecutor;
import java.lang.ref.WeakReference;


public class RealCriteoBannerEventController implements CriteoBannerEventController {

  @NonNull
  private final WeakReference<CriteoBannerView> view;

  @Nullable
  private final CriteoBannerAdListener adListener;

  @NonNull
  private final Criteo criteo;

  @NonNull
  private final TopActivityFinder topActivityFinder;

  @NonNull
  private final RunOnUiThreadExecutor executor;

  public RealCriteoBannerEventController(
      @NonNull CriteoBannerView bannerView,
      @NonNull Criteo criteo,
      @NonNull TopActivityFinder topActivityFinder,
      @NonNull RunOnUiThreadExecutor runOnUiThreadExecutor
  ) {
    this.view = new WeakReference<>(bannerView);
    this.adListener = bannerView.getCriteoBannerAdListener();
    this.criteo = criteo;
    this.topActivityFinder = topActivityFinder;
    this.executor = runOnUiThreadExecutor;
  }

  @Override
  public void fetchAdAsync(@Nullable AdUnit adUnit) {
    Slot slot = criteo.getBidForAdUnit(adUnit);

    if (slot == null) {
      notifyFor(INVALID);
    } else {
      notifyFor(VALID);
      displayAd(slot.getDisplayUrl());
    }
  }

  @Override
  public void fetchAdAsync(@Nullable BidToken bidToken) {
    TokenValue tokenValue = criteo.getTokenValue(bidToken, AdUnitType.CRITEO_BANNER);

    if (tokenValue == null) {
      notifyFor(INVALID);
    } else {
      notifyFor(VALID);
      displayAd(tokenValue.getDisplayUrl());
    }
  }

  private void notifyFor(@NonNull CriteoListenerCode code) {
    executor.executeAsync(new CriteoBannerListenerCallTask(adListener, view, code));
  }

  @VisibleForTesting
  void displayAd(@NonNull String displayUrl) {
    executor.executeAsync(new CriteoBannerLoadTask(
        view, createWebViewClient(), criteo.getConfig(), displayUrl));
  }

  // WebViewClient is created here to prevent passing the AdListener everywhere.
  // Setting this webViewClient to the WebView is done in the CriteoBannerLoadTask as all
  // WebView methods need to run in the same UI thread
  @VisibleForTesting
  WebViewClient createWebViewClient() {
    // Even if we have access to the view here, we're not sure that publisher gave an activity
    // context to the view. So we're getting the activity by this way.
    ComponentName bannerActivityName = topActivityFinder.getTopActivityName();

    return new AdWebViewClient(new AdWebViewListener() {
      @Override
      public void onUserRedirectedToAd() {
        notifyFor(CLICK);
      }

      @Override
      public void onUserBackFromAd() {
        notifyFor(CLOSE);
      }
    }, bannerActivityName);
  }

}