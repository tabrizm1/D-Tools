package com.github.dust2.tools.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import com.github.dust2.tools.AppConfig.BEST_CDN_IP_RESULT
import com.github.dust2.tools.AppConfig.MAX_CDN_IPS
import com.github.dust2.tools.AppConfig.MAX_LATENCY
import com.github.dust2.tools.R
import com.github.dust2.tools.databinding.LayoutBestCdnIpBinding
import com.github.dust2.tools.util.MmkvManager
import com.github.dust2.tools.util.Utils
import com.github.dust2.tools.util.onMainDispatcher
import com.github.dust2.tools.util.runOnIoDispatcher
import libcore.Libcore
import java.net.InetAddress


class BestCdnIpActivity : BaseActivity() {

    private lateinit var binding: LayoutBestCdnIpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.best_cdn_ip)
        binding = LayoutBestCdnIpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ipTest.setOnClickListener {
            findBestCdnIp(this)
        }

        binding.idCopy.setOnClickListener {
            Utils.setClipboard(this, binding.bestCdnIpResult.text.toString())
        }

        val maxCdnIps = MmkvManager.getSetting().decodeInt(MAX_CDN_IPS, 10)
        val maxLatency = MmkvManager.getSetting().decodeInt(MAX_LATENCY, 300)
        val bestCdnIpResult = MmkvManager.getSetting().decodeString(BEST_CDN_IP_RESULT, "")

        binding.maxCdnIps.text = Utils.getEditable(maxCdnIps.toString())
        binding.maxLatency.text = Utils.getEditable(maxLatency.toString())
        binding.bestCdnIpResult.text = bestCdnIpResult
    }

    private fun findBestCdnIp(context: Context) {
        val cidrs = Utils.readTextFromAssets(context.applicationContext, "cf_cidrs_v4").lines()
        val maxIp = Utils.parseInt(binding.maxCdnIps.text.toString(), 10)
        val maxLatency = Utils.parseInt(binding.maxLatency.text.toString(), 300)

        MmkvManager.getSetting().encode(MAX_CDN_IPS, maxIp)
        MmkvManager.getSetting().encode(MAX_LATENCY, maxLatency)
        binding.bestCdnIpResult.text = ""

        doFindBestCdnIp(cidrs, maxIp, maxLatency)
    }

    private fun doFindBestCdnIp(cidrs: List<String>, maxIp: Int, latency: Int) {
        binding.idProgress.isVisible = true
        binding.ipTest.isVisible = false

        runOnIoDispatcher {
            val bestIps =
                try {
                    val cdnIPList = getCdnIpList(cidrs)

                    val bestIps: MutableList<Pair<String, Int>> = mutableListOf()
                    for (ip in cdnIPList) {
                        val result = try {
                            Libcore.icmpPing(ip, latency + 300)
                        } catch (e: Exception) {
                            -1
                        }
                        if (result in 1..latency) {
                            bestIps.add(Pair(ip, result))
                        }
                        Log.d("doFindBestCdnIp", ip + "===" + result.toString())
                        if (bestIps.count() >= maxIp) {
                            break
                        }
                    }
                    bestIps
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            onMainDispatcher {
                val str = ipsToString(bestIps)
                binding.bestCdnIpResult.text = str
                MmkvManager.getSetting().encode(BEST_CDN_IP_RESULT, str)
                binding.idProgress.isVisible = false
                binding.ipTest.isVisible = true
            }
        }
    }

    private fun getCdnIpList(cidrList: List<String>, maxPerRange: Int = 3): List<String> {
        val ipList: MutableList<String> = mutableListOf()

        cidrList.forEach { cidr ->
            val ip = cidr.split('/')[0]
            val ipParts = ip.split(".").toTypedArray()
            (1..254).shuffled().take(maxPerRange).forEach {
                ipParts[3] = it.toString()
                ipList += ipParts.joinToString(".")
            }
        }

        return ipList.shuffled()
    }

    private fun ipsToString(bestIps: MutableList<Pair<String, Int>>?): String? {
        return bestIps?.map { it.first }?.toList()?.joinToString(separator = "\n")
    }

}