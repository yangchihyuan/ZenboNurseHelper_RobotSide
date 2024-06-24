
package tw.edu.cgu.ai.zenbo;

import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.RobotCmdState;
import com.asus.robotframework.API.RobotErrorCode;

public class ZenboCallback extends RobotCallback {

    public boolean RobotMovementFinished_Head = true;
    public boolean RobotMovementFinished_Body = true;
    public long TimeStamp_MovementFinished_Head = Long.MIN_VALUE;
    public long TimeStamp_MovementFinished_Body = Long.MIN_VALUE;
    public long TimeStamp_MovementHead_Active = 0;      //can not use Long.Max_Value, out of range after the subtraction.
    public long TimeStamp_MovementBody_Active = 0;

    @Override
    public void onStateChange(int cmd, int serial, RobotErrorCode err_code, RobotCmdState state) {
        //check, what will happen is the power core is plugged?
        if (cmd == 39) {        //cmd == 39 movebody
//            Log.i("ZenboCallback", state.toString());
            if (state == RobotCmdState.ACTIVE) {
                //It happen this callback is erased by the Zenbo system prompt.
                RobotMovementFinished_Body = false;
                TimeStamp_MovementBody_Active = System.currentTimeMillis();
            } else if (state == RobotCmdState.SUCCEED) {
                RobotMovementFinished_Body = true;
                TimeStamp_MovementFinished_Body = System.currentTimeMillis();
            } else if (state == RobotCmdState.PENDING) {
                //When does it happen? When Zenbo finds an obstacle.
                RobotMovementFinished_Body = false;
            } else if (state == RobotCmdState.REJECTED) {
                //it happens when Zenbo is updating its language dataset.
                //or the power core is plugged, the RobotCmdState.SUCCEED will never come.
                RobotMovementFinished_Body = true;
                TimeStamp_MovementFinished_Body = System.currentTimeMillis();
            } else if (state == RobotCmdState.FAILED){
                //it happens when Zenbo fails to move to the expected location
                RobotMovementFinished_Body = true;
                TimeStamp_MovementFinished_Body = System.currentTimeMillis();
            } else if (state == RobotCmdState.INITIAL) {
                //I never saw this state
                RobotMovementFinished_Body = false;
            }else{
                RobotMovementFinished_Body = false;
            }

        }

        if (cmd == 6)        //cmd == 6, moveHead
        {
            //2018/5/28 Chih-Yuan: The callback is unstable. It happens that there is no more callback after ACTIVE
            if (state == RobotCmdState.ACTIVE) {
                RobotMovementFinished_Head = false;
                TimeStamp_MovementHead_Active = System.currentTimeMillis();
            } else if (state == RobotCmdState.SUCCEED) {
                RobotMovementFinished_Head = true;
                TimeStamp_MovementFinished_Head = System.currentTimeMillis();
            } else if (state == RobotCmdState.PENDING) {
                //it happens when Zenbo is waiting the user's permit to move.
                //2018/5/28: There is a bug. Zenbo won't send another state after sending this state. Thus I have to set this to true;
                RobotMovementFinished_Head = true;
                TimeStamp_MovementFinished_Head = System.currentTimeMillis();
            } else if (state == RobotCmdState.REJECTED) {
                //it happens when Zenbo is updating its language dataset.
                RobotMovementFinished_Head = true;
                TimeStamp_MovementFinished_Head = System.currentTimeMillis();
            } else if (state == RobotCmdState.FAILED){
                //it happens along with an err_code: MOTION_OUT_OF_BOUNDS, if we use an argument out of range
                RobotMovementFinished_Body = true;
                TimeStamp_MovementFinished_Head = System.currentTimeMillis();
            } else {
                TimeStamp_MovementFinished_Head = System.currentTimeMillis();
                RobotMovementFinished_Head = true;
            }
        }

        super.onStateChange(cmd, serial, err_code, state);
    }

}
