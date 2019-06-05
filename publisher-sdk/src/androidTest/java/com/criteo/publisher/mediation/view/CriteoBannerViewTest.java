package com.criteo.publisher.mediation.view;

import android.app.Application;
import android.support.test.InstrumentationRegistry;
import android.test.UiThreadTest;
import com.criteo.publisher.Criteo;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.mediation.listeners.CriteoBannerAdListener;
import com.criteo.publisher.mediation.utils.CriteoErrorCode;
import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.CacheAdUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CriteoBannerViewTest {

    @Mock
    private CriteoBannerAdListener criteoBannerAdListener;

    private CriteoBannerView criteoBannerView;


    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        BannerAdUnit bannerAdUnit = new BannerAdUnit("/140800857/None", new AdSize(50, 320));
        List<AdUnit> AdUnits = new ArrayList<>();
        AdUnits.add(bannerAdUnit);
        Application app =
                (Application) InstrumentationRegistry
                        .getTargetContext()
                        .getApplicationContext();
        Criteo.init(app, AdUnits, "4916");
        criteoBannerView = new CriteoBannerView(InstrumentationRegistry.getContext(), bannerAdUnit);
        criteoBannerView.setCriteoBannerAdListener(criteoBannerAdListener);
    }


    @Test
    public void testNotifyListenerAsyncWithNullSlot() throws InterruptedException {
        criteoBannerView.loadAd();

        //wait for the loadAd process to be completed
        Thread.sleep(500);

        //Expected result , found no slot and called criteoBannerAdListener.onAdFetchFailed
        Mockito.verify(criteoBannerAdListener, Mockito.times(0)).onAdFetchSucceededForBanner(criteoBannerView);
        Mockito.verify(criteoBannerAdListener, Mockito.times(1)).onAdFetchFailed(CriteoErrorCode.ERROR_CODE_NO_FILL);
    }

}