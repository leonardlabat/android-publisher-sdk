package com.criteo.publisher;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.criteo.publisher.AppEvents.AppEvents;
import com.criteo.publisher.Util.AdvertisingInfo;
import com.criteo.publisher.Util.AndroidUtil;
import com.criteo.publisher.Util.BuildConfigWrapper;
import com.criteo.publisher.Util.CustomAdapterFactory;
import com.criteo.publisher.Util.DeviceUtil;
import com.criteo.publisher.Util.JsonSerializer;
import com.criteo.publisher.Util.LoggingUtil;
import com.criteo.publisher.Util.RunOnUiThreadExecutor;
import com.criteo.publisher.bid.BidLifecycleListener;
import com.criteo.publisher.bid.LoggingBidLifecycleListener;
import com.criteo.publisher.bid.UniqueIdGenerator;
import com.criteo.publisher.cache.SdkCache;
import com.criteo.publisher.csm.MetricParser;
import com.criteo.publisher.csm.MetricSendingQueue;
import com.criteo.publisher.csm.MetricSendingQueueConsumer;
import com.criteo.publisher.csm.MetricSendingQueueFactory;
import com.criteo.publisher.interstitial.InterstitialActivityHelper;
import com.criteo.publisher.model.AdUnitMapper;
import com.criteo.publisher.model.CdbRequestFactory;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.DeviceInfo;
import com.criteo.publisher.model.Publisher;
import com.criteo.publisher.model.RemoteConfigRequestFactory;
import com.criteo.publisher.network.BidRequestSender;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.privacy.UserPrivacyUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Provides global dependencies to the rest of the codebase
 */
public class DependencyProvider {

  protected static DependencyProvider instance;

  private final Map<Class, Object> services = new HashMap<>();

  private DependencyProvider() {
  }

  @NonNull
  public static synchronized DependencyProvider getInstance() {
    if (instance == null) {
      instance = new DependencyProvider();
    }
    return instance;
  }

  /** KEEP VISIBILITY AS PACKAGE-PRIVATE **/
  /**
   * This method will be used by tests to provide a fake {@link DependencyProvider} instance
   */
  static synchronized void setInstance(@Nullable DependencyProvider dependencyProvider) {
    instance = dependencyProvider;
  }

  @NonNull
  public PubSdkApi providePubSdkApi() {
    return getOrCreate(PubSdkApi.class, new Factory<PubSdkApi>() {
      @Override
      public PubSdkApi create() {
        return new PubSdkApi(
            provideBuildConfigWrapper(),
            provideJsonSerializer()
        );
      }
    });
  }

  @NonNull
  public AdvertisingInfo provideAdvertisingInfo() {
    return getOrCreate(AdvertisingInfo.class, new Factory<AdvertisingInfo>() {
      @Override
      public AdvertisingInfo create() {
        return new AdvertisingInfo();
      }
    });
  }

  @NonNull
  public AndroidUtil provideAndroidUtil(@NonNull Context context) {
    return getOrCreate(AndroidUtil.class, new Factory<AndroidUtil>() {
      @Override
      public AndroidUtil create() {
        return new AndroidUtil(context);
      }
    });
  }

  @NonNull
  public DeviceUtil provideDeviceUtil(@NonNull Context context) {
    return getOrCreate(DeviceUtil.class, new Factory<DeviceUtil>() {
      @Override
      public DeviceUtil create() {
        return new DeviceUtil(context, DependencyProvider.this.provideAdvertisingInfo());
      }
    });
  }

  @NonNull
  public LoggingUtil provideLoggingUtil() {
    return getOrCreate(LoggingUtil.class, new Factory<LoggingUtil>() {
      @Override
      public LoggingUtil create() {
        return new LoggingUtil();
      }
    });
  }

  @NonNull
  public Executor provideThreadPoolExecutor() {
    return AsyncTask.THREAD_POOL_EXECUTOR;
  }

  @NonNull
  public Executor provideSerialExecutor() {
    return AsyncTask.SERIAL_EXECUTOR;
  }

  @NonNull
  public RunOnUiThreadExecutor provideRunOnUiThreadExecutor() {
    return getOrCreate(RunOnUiThreadExecutor.class, new Factory<RunOnUiThreadExecutor>() {
      @Override
      public RunOnUiThreadExecutor create() {
        return new RunOnUiThreadExecutor();
      }
    });
  }

  @NonNull
  public Config provideConfig(Context context) {
    return getOrCreate(Config.class, new Factory<Config>() {
      @Override
      public Config create() {
        return new Config(context);
      }
    });
  }

  @NonNull
  public Clock provideClock() {
    return getOrCreate(Clock.class, new Factory<Clock>() {
      @Override
      public Clock create() {
        return new EpochClock();
      }
    });
  }

  @NonNull
  public UserPrivacyUtil provideUserPrivacyUtil(@NonNull Context context) {
    return getOrCreate(UserPrivacyUtil.class, new Factory<UserPrivacyUtil>() {
      @Override
      public UserPrivacyUtil create() {
        return new UserPrivacyUtil(context);
      }
    });
  }

  @NonNull
  public BidManager provideBidManager(
      @NonNull Context context,
      @NonNull String criteoPublisherId) {
    return getOrCreate(BidManager.class, new Factory<BidManager>() {
      @Override
      public BidManager create() {
        return new BidManager(
            new SdkCache(DependencyProvider.this.provideDeviceUtil(context)),
            DependencyProvider.this.provideConfig(context),
            DependencyProvider.this.provideClock(),
            DependencyProvider.this.provideAdUnitMapper(context),
            DependencyProvider.this.provideBidRequestSender(context, criteoPublisherId),
            DependencyProvider.this.provideBidLifecycleListener(),
            DependencyProvider.this.provideMetricSendingQueueConsumer()
        );
      }
    });
  }

  @NonNull
  public DeviceInfo provideDeviceInfo(Context context) {
    return getOrCreate(DeviceInfo.class, new Factory<DeviceInfo>() {
      @Override
      public DeviceInfo create() {
        return new DeviceInfo(
            context,
            DependencyProvider.this.provideRunOnUiThreadExecutor());
      }
    });
  }

  @NonNull
  public AdUnitMapper provideAdUnitMapper(Context context) {
    return getOrCreate(AdUnitMapper.class, new Factory<AdUnitMapper>() {
      @Override
      public AdUnitMapper create() {
        return new AdUnitMapper(
            DependencyProvider.this.provideAndroidUtil(context),
            DependencyProvider.this.provideDeviceUtil(context));
      }
    });
  }

  @NonNull
  public AppEvents provideAppEvents(@NonNull Context context) {
    return getOrCreate(AppEvents.class, new Factory<AppEvents>() {
      @Override
      public AppEvents create() {
        return new AppEvents(
            context,
            DependencyProvider.this.provideDeviceUtil(context),
            DependencyProvider.this.provideClock(),
            DependencyProvider.this.providePubSdkApi(),
            DependencyProvider.this.provideUserPrivacyUtil(context),
            DependencyProvider.this.provideDeviceInfo(context)
        );
      }
    });
  }

  @NonNull
  public Publisher providePublisher(@NonNull Context context, @NonNull String criteoPublisherId) {
    return getOrCreate(Publisher.class, new Factory<Publisher>() {
      @Override
      public Publisher create() {
        return new Publisher(context, criteoPublisherId);
      }
    });
  }

  @NonNull
  public BuildConfigWrapper provideBuildConfigWrapper() {
    return getOrCreate(BuildConfigWrapper.class, new Factory<BuildConfigWrapper>() {
      @Override
      public BuildConfigWrapper create() {
        return new BuildConfigWrapper();
      }
    });
  }

  @NonNull
  public CdbRequestFactory provideCdbRequestFactory(@NonNull Context context, @NonNull String criteoPublisherId) {
    return getOrCreate(CdbRequestFactory.class, new Factory<CdbRequestFactory>() {
      @Override
      public CdbRequestFactory create() {
        return new CdbRequestFactory(
            providePublisher(context, criteoPublisherId),
            provideDeviceInfo(context),
            provideDeviceUtil(context),
            provideUserPrivacyUtil(context),
            new UniqueIdGenerator(provideClock()),
            provideBuildConfigWrapper()
        );
      }
    });
  }

  @NonNull
  public RemoteConfigRequestFactory provideRemoteConfigRequestFactory(@NonNull Context context, @NonNull String criteoPublisherId) {
    return getOrCreate(RemoteConfigRequestFactory.class, new Factory<RemoteConfigRequestFactory>() {
      @Override
      public RemoteConfigRequestFactory create() {
        return new RemoteConfigRequestFactory(
            providePublisher(context, criteoPublisherId),
            provideBuildConfigWrapper()
        );
      }
    });
  }

  @NonNull
  public BidRequestSender provideBidRequestSender(@NonNull Context context, @NonNull String criteoPublisherId) {
    return getOrCreate(BidRequestSender.class, new Factory<BidRequestSender>() {
      @Override
      public BidRequestSender create() {
        return new BidRequestSender(
            provideCdbRequestFactory(context, criteoPublisherId),
            provideRemoteConfigRequestFactory(context, criteoPublisherId),
            providePubSdkApi(),
            provideThreadPoolExecutor()
        );
      }
    });
  }

  @NonNull
  public BidLifecycleListener provideBidLifecycleListener() {
    return getOrCreate(BidLifecycleListener.class, new Factory<BidLifecycleListener>() {
      @Override
      public BidLifecycleListener create() {
        return new LoggingBidLifecycleListener(
            provideLoggingUtil()
        );
      }
    });
  }

  @SuppressWarnings("unchecked")
  private <T> T getOrCreate(Class<T> klass, Factory<T> factory) {
    Object service = services.get(klass);
    if (service != null) {
      // safe because the services map is only filled there by typed factory
      return (T) service;
    }

    T newService = factory.create();
    services.put(klass, newService);
    return newService;
  }

  @NonNull
  public InHouse provideInHouse(@NonNull Context context, @NonNull String criteoPublisherId) {
    return getOrCreate(InHouse.class, new Factory<InHouse>() {
      @Override
      public InHouse create() {
        return new InHouse(
            DependencyProvider.this.provideBidManager(context, criteoPublisherId),
            new TokenCache(),
            DependencyProvider.this.provideClock(),
            DependencyProvider.this.provideInterstitialActivityHelper(context));
      }
    });
  }

  @NonNull
  public InterstitialActivityHelper provideInterstitialActivityHelper(@NonNull Context context) {
    return getOrCreate(InterstitialActivityHelper.class, new Factory<InterstitialActivityHelper>() {
      @Override
      public InterstitialActivityHelper create() {
        return new InterstitialActivityHelper(context);
      }
    });
  }

  @NonNull
  public MetricSendingQueueConsumer provideMetricSendingQueueConsumer() {
    return getOrCreate(MetricSendingQueueConsumer.class, new Factory<MetricSendingQueueConsumer>() {
      @Override
      public MetricSendingQueueConsumer create() {
        return new MetricSendingQueueConsumer(
            provideMetricSendingQueue(),
            providePubSdkApi(),
            provideBuildConfigWrapper(),
            provideThreadPoolExecutor()
        );
      }
    });
  }

  @NonNull
  public MetricSendingQueue provideMetricSendingQueue() {
    return getOrCreate(MetricSendingQueue.class, new MetricSendingQueueFactory());
  }

  @NonNull
  public MetricParser provideMetricParser() {
    return getOrCreate(MetricParser.class, new Factory<MetricParser>() {
      @Override
      public MetricParser create() {
        return new MetricParser(
            provideGson(),
            provideJsonSerializer()
        );
      }
    });
  }

  @NonNull
  public JsonSerializer provideJsonSerializer() {
    return getOrCreate(JsonSerializer.class, new Factory<JsonSerializer>() {
      @Override
      public JsonSerializer create() {
        return new JsonSerializer(provideGson());
      }
    });
  }

  @NonNull
  public Gson provideGson() {
    return getOrCreate(Gson.class, new Factory<Gson>() {
      @Override
      public Gson create() {
        return new GsonBuilder()
            .registerTypeAdapterFactory(CustomAdapterFactory.create())
            .create();
      }
    });
  }

  public interface Factory<T> {

    T create();
  }

}
