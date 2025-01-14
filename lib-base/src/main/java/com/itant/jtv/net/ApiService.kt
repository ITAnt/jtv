package com.itant.jtv.net

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url


/**
 * 请求接口
 * @date 2021-8-7 11:36
 * @author 詹子聪
 * 请求接口，统一规则，BaseUrl以/结尾，具体请求不能以/开头
 * 注意：函数必须是suspend的
 */
interface ApiService {

    @GET
    suspend fun getHtmlResponse(@Url url: String): Response<ResponseBody>
}