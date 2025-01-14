package com.itant.jtv.net

import com.blankj.utilcode.util.AppUtils
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.itant.jtv.BuildConfig
import com.miekir.mvvm.context.GlobalContext
import com.miekir.mvvm.task.net.RetrofitManager


/**
 * 网络请求封装
 * @date 2021-8-7 11:32
 * @author 詹子聪
 */
object ApiManager {
    /**
     * 默认的网络请求
     */
    val default by lazy {
        RetrofitManager.getDefault()
            .timeout(5000, 5000, 5000, 5000)
            .addInterceptors(ChuckerInterceptor.Builder(GlobalContext.getContext()).build())
            .printLog(AppUtils.isAppDebug())
            .createApiService(BuildConfig.BASE_URL, ApiService::class.java)
    }

    val spider by lazy {
        RetrofitManager.newInstance()
            //.ssl(SSLSocketClient.getSSLSocketFactory())
            //.header("Connection", "keep-alive")
            //.header("Accept", "*/*")
            //.header("User-Agent", "PostmanRuntime/7.42.0")
            //.header("Accept-Encoding", "gzip, deflate, br")
            .header("CN_GA", "CN_GA")
            .timeout(60000, 60000, 60000, 60000)
            .addInterceptors(ChuckerInterceptor.Builder(GlobalContext.getContext()).build())
            .printLog(AppUtils.isAppDebug())
            /*.apply {
                // Create a trust manager that does not validate certificate chains
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        @Throws(CertificateException::class)
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                            L.e("checkClientTrusted")
                        }

                        @Throws(CertificateException::class)
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                            L.e("checkServerTrusted")
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> {
                            return arrayOf()
                        }
                    }
                )
                // Install the all-trusting trust manager
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())

                // Create an ssl socket factory with our all-trusting manager
                val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

                getClientBuilder().sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                getClientBuilder().hostnameVerifier { _, _ -> true }
            }*/
            .createApiService(BuildConfig.BASE_URL, ApiService::class.java)

    }
}