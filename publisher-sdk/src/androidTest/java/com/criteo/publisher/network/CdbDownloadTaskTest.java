package com.criteo.publisher.network;

import android.content.Context;
import com.criteo.publisher.Util.AdUnitType;
import com.criteo.publisher.Util.DeviceUtil;
import com.criteo.publisher.Util.LoggingUtil;
import com.criteo.publisher.Util.NetworkResponseListener;
import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.CacheAdUnit;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class  CdbDownloadTaskTest {

    private CdbDownloadTask cdbDownloadTask;
    private List<CacheAdUnit> cacheAdUnits;

    @Mock
    private Context mContext;

    @Mock
    private NetworkResponseListener responseListener;

    @Mock
    private Hashtable<CacheAdUnit, CdbDownloadTask> bidsInCdbTask;

    @Mock
    private DeviceUtil deviceUtil;

    @Mock
    private LoggingUtil loggingUtil;

    @Test
    public void checkCacheRemove() {
        MockitoAnnotations.initMocks(this);
        cacheAdUnits = new ArrayList<>();
        CacheAdUnit cacheAdUnit = new CacheAdUnit(new AdSize(320, 50), "UniqueId", AdUnitType.CRITEO_BANNER);
        cacheAdUnits.add(cacheAdUnit);

        cdbDownloadTask = new CdbDownloadTask(
            mContext,
            responseListener,
            false,
            "Agent",
            cacheAdUnits,
            bidsInCdbTask,
            deviceUtil,
            loggingUtil
        );

        cdbDownloadTask.onPostExecute(null);

        Mockito.verify(bidsInCdbTask, Mockito.times(1)).remove(cacheAdUnit);
    }


}