package com.edillower.heymavic.flightcontrol;


import android.content.Context;
import android.util.Log;

import com.edillower.heymavic.common.DJISimulatorApplication;
import com.edillower.heymavic.common.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.timeline.Mission;
import dji.sdk.mission.timeline.TimelineElement;
import dji.sdk.mission.timeline.TimelineEvent;
import dji.sdk.mission.timeline.actions.GoToAction;
import dji.sdk.mission.timeline.actions.MissionAction;
import dji.sdk.mission.timeline.triggers.Trigger;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;

public class CommandInterpreter {
    private Context mContext;

    public Aircraft aircraft; //need to be local, should not be declare here
    public FlightController mFlightController; //need to be private
    public boolean mVirtualStickEnabled=false;
    /*
    do not use in this way
    use DJIFlightControllerFlightMode (Enum)
    isVirtualStickControlModeAvailable()
    */
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private GoToAction mGoToAction;

    /**
     * default constructor
     * @param context
     */
    public CommandInterpreter(Context context) {
        mContext = context;
    }

    /**
     * should be private, not be used by others
     * now it's used by FPVFullscreen
     * */
    public void initFlightController() {
        aircraft = DJISimulatorApplication.getAircraftInstance();

        if (aircraft == null || !aircraft.isConnected()) {
            Utils.setResultToToast(mContext, "aircraft not found!");
            mFlightController = null;
        } else {
            mFlightController = aircraft.getFlightController();
//            MyChangeSettingsExecutor.mEnableVS(); //this should be deleted after set to private
//            Utils.setResultToToast(mContext, "CI init FlightController success with mode "+mFlightController.getState().getFlightMode());
        }
    }

    /**
     * This is completed !
     * directly call on FlightController
     * */
    private void mTakeoff(){
        if (mFlightController != null){
            mFlightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

    /**
     * This is completed !
     * directly call on FlightController
     *
     * */
    private void mLand(){
        if (mFlightController != null){
            mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

    /**
     * need to be private this one
     *
     * should stop VirtualStick's working or Mission
     */
    public void mStop(){
        if(mFlightController.isVirtualStickControlModeAvailable()){
            Utils.setResultToToast(mContext, "VS is on");
            mSingletonVirtualStickExecutor.mStop();
        }else if(mGoToAction!=null&&mGoToAction.isRunning()){
            mGoToAction.stop();
        }else{
            MyChangeSettingsExecutor.mEnableVS();
            mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
            mSingletonVirtualStickExecutor.mStop();
        }
    }

    /**
     *
     * @param mEncoded
     */
    public void executeCmd(ArrayList<Integer> mEncoded) {
        int len = mEncoded.size();
        int[] mCmdCode = new int[len];
        for (int i = 0; i < len; i++) {
            mCmdCode[i] = mEncoded.get(i);
        }
        if(mCmdCode.length == 0){
            Utils.setResultToToast(mContext, "Wrong Command Code [null]");
            return;
        }
        initFlightController();
        int idx = 0, para_dir_go = 301, para_dir = 303, para_dis = -1, para_deg = 90;
//        Utils.setResultToToast(mContext, "before cmd mode:"+mFlightController.getState().getFlightMode());

        if(mGoToAction!=null&&mGoToAction.isRunning()){
            mGoToAction.stop();
        }

        switch (mCmdCode[idx]) {
            case 100:
                mTakeoff();
                break;
            case 101:
                mLand();
                break;
            case 102:
                mStop();
                break;
            case 103:
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==201){
                    para_dir_go = mCmdCode[idx+2];
                    idx+=2;
                }else{
                    Utils.setResultToToast(mContext, "Wrong Command Code [103]");
                }
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==202){
                    para_dis = mCmdCode[idx+2];
                    idx+=2;
                }
                if(para_dis==-1){
                    mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                    mSingletonVirtualStickExecutor.mGo(para_dir_go);
                }else{
                    //1. should reivew order here
                    //2. stop when other cmd come in
                    MyVirtualStickExecutor.destroyInstance();
                    double mHomeLatitude = mFlightController.getState().getAircraftLocation().getLatitude();
                    double mHomeLongitude = mFlightController.getState().getAircraftLocation().getLongitude();
                    double bearing = mFlightController.getCompass().getHeading();
                    if(para_dir_go==302){
                        bearing += 180;
                    }else if(para_dir_go==303){
                        bearing -= 90;
                    }else if(para_dir_go==304){
                        bearing += 90;
                    }
                    double[] des = Utils.calcDestination(mHomeLatitude, mHomeLongitude, bearing, para_dis);
                    final double aa = des[0];
                    final double bb = des[1];

                    Runnable goDis = new Runnable() {
                        @Override
                        public void run() {
                            try{
                                TimeUnit.SECONDS.sleep(2);
                            }catch (Exception e){

                            }
                            MyChangeSettingsExecutor.mDisableVS();
                            if(mFlightController.isVirtualStickControlModeAvailable()){
                                Utils.setResultToToast(mContext, "VS still on");
                            }
                            mGoToAction = new GoToAction(new LocationCoordinate2D(aa,bb));
                            mGoToAction.run();
                            try{
                                TimeUnit.SECONDS.sleep(2);
                            }catch (Exception e){

                            }
                            mGoToAction.didRun();
                        }
                    };

                    goDis.run();

                }
                break;
            case 104:
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==203){
                    para_dir = mCmdCode[idx+2];
                    idx += 2;
                }else{
                    Utils.setResultToToast(mContext, "Wrong Command Code [104]");
                }
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==204){
                    para_deg = mCmdCode[idx+2];
                    idx += 2;
                }
                mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                mSingletonVirtualStickExecutor.mTurn(para_dir,para_deg);
                break;
            case 105:
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==202){
                    para_dis = mCmdCode[idx+2];
                    idx+=2;
                }
                if(para_dis == -1){
                    Utils.setResultToToast(mContext, "Up no dis");
                    mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                    mSingletonVirtualStickExecutor.mUp();
                }else{
                    //1. should reivew order here
                    //2. stop when other cmd come in
                    Utils.setResultToToast(mContext, "Up dis: " + Integer.toString(para_dis));
                    MyChangeSettingsExecutor.mDisableVS();
                    MyVirtualStickExecutor.destroyInstance();

                    mGoToAction = new GoToAction(para_dis);
                    mGoToAction.run();
                }
                break;
            case 106:
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==202){
                    para_dis = mCmdCode[idx+2];
                    idx+=2;
                }
                if(para_dis == -1){
                    mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                    mSingletonVirtualStickExecutor.mDown();
                }else{
                    //1. should reivew order here
                    //2. stop when other cmd come in
                    MyChangeSettingsExecutor.mDisableVS();
                    MyVirtualStickExecutor.destroyInstance();

                    mGoToAction = new GoToAction(0-para_dis);
                    mGoToAction.run();
                }
                break;
            case 107:
                int para_lati_int=25, para_lati_decimal=0, para_logi_int=113, para_logi_decimal=0;

                if(idx+5<mCmdCode.length && mCmdCode[idx+1]==205){
                    para_lati_int = mCmdCode[idx+2];
                    para_lati_decimal = mCmdCode[idx+3];
                    para_logi_int = mCmdCode[idx+4];
                    para_logi_decimal = mCmdCode[idx+5];
                    idx+=5;
                }else{
                    Utils.setResultToToast(mContext, "Wrong Command Code [107]");
                }
                //1. should reivew order here
                //2. stop when other cmd come in
                MyChangeSettingsExecutor.mDisableVS();
                MyVirtualStickExecutor.destroyInstance();

                double para_lati = para_lati_int + (double) para_lati_decimal/100000.0;
                double para_logi = para_logi_int + (double) para_logi_decimal/100000.0;
                Utils.setResultToToast(mContext, para_lati+","+para_logi);
                //=======================
                mGoToAction = new GoToAction(new LocationCoordinate2D(para_lati, para_logi));
                MissionControl mc = MissionControl.getInstance();
                mc.scheduleElement(mGoToAction);
                mc.startTimeline();
                //=======================

                break;
            case 108:
                int set_type = 0, set_param = 0;
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==206){
                    set_type= mCmdCode[idx+2];
                    idx += 2;
                }else{
                    Utils.setResultToToast(mContext, "Wrong Command Code [108]");
                }
                if(idx+2<mCmdCode.length && mCmdCode[idx+1]==207){
                    set_param = mCmdCode[idx+2];
                    idx += 2;
                }
//                MyChangeSettingsExecutor.execute(set_type, set_param);
                break;
            case 109:
                break;
            default:
                mStop();
                break;
        }
//        Utils.setResultToToast(mContext, "after cmd mode:"+mFlightController.getState().getFlightMode());
    }
}