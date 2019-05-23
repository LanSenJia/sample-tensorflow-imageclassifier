package com.example.androidthings.imageclassifier;

import com.baidu.tts.client.TtsMode;
import com.example.androidthings.imageclassifier.utils.OfflineResource;

/*
 * @Title:
 * @Copyright:  GuangZhou F.R.O Electronic Technology Co.,Ltd. Copyright 2006-2016,  All rights reserved
 * @Descrplacetion:  ${TODO}<请描述此文件是做什么的>
 * @author:  lansenboy
 * @data: 2019/5/23
 * @version:  V1.0
 * @OfficialWebsite: http://www.frotech.com/
 */
public class Contant {

    protected  static String appId = "11005757";

    protected static String appKey = "Ovcz19MGzIKoDDb3IsFFncG1";

    protected static String secretKey = "e72ebb6d43387fc7f85205ca7e6706e2";

    protected static TtsMode ttsMode = TtsMode.MIX;

    // 离线发音选择，VOICE_FEMALE即为离线女声发音。
    // assets目录下bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat为离线男声模型；
    // assets目录下bd_etts_common_speech_f7_mand_eng_high_am-mix_v3.0.0_20170512.dat为离线女声模型
    protected  static String offlineVoice = OfflineResource.VOICE_MALE;
}
