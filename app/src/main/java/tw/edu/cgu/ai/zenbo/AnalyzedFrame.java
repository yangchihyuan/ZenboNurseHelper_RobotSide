package tw.edu.cgu.ai.zenbo;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class AnalyzedFrame {
    long timestamp_OnImageAvailable;
    long timestamp_ReceivedFromServer;
    int pitchDegree;
    boolean bNew = false;
    int openpose_cnt;
    int yolo_cnt_person;
    int yolo_cnt_tvmonitor;
    List<float[][]> openpose_coordinate;
    List<int[]> yolo_coordinate_person;  //Chih-Yuan Yang 2024: I no longer use this because I disable Yolo.
    List<int[]> yolo_coordinate_tvmonitor; //Chih-Yuan Yang 2024: I no longer use this because I disable Yolo.
    float[][] fMatrix;
    boolean bFoundPerson = false;
    boolean bIgnorePerson = false;
    boolean bAvailable = false;
    String[] actions = {"",""};     //8/23/2018 Chih-Yuan: I need to modify this statement later
    int tracker_roi_x = -1;
    int tracker_roi_y = -1;
    int tracker_roi_width = -1;
    int tracker_roi_height = -1;
    int roi_rectangle_color = 0;

    public AnalyzedFrame()
    {
        fMatrix = new float[18][3];
        openpose_coordinate = new ArrayList<float[][]>();
        yolo_coordinate_person = new ArrayList<int[]>();
        yolo_coordinate_tvmonitor = new ArrayList<int[]>();
    }

    private int find_max_value_in_numeric_array_with_java ( float[] numbers) {
        int index_max = 0;
        float highest = numbers[0];
        for (int index = 1; index < numbers.length; index ++) {
            if (numbers[index] > highest) {
                highest = numbers [index];
                index_max = index;
            }
        }
        return index_max;
    }

    public void ParseServerReturn(String ServerReturns)
    {
        //clear old data
        openpose_coordinate.clear();
        yolo_coordinate_person.clear();
        yolo_coordinate_tvmonitor.clear();

        String[] protobuf_result = ServerReturns.split(System.getProperty("line.separator"));

        //TODO: use protobuf to clarify it
        for(String pr : protobuf_result){
            if(pr.contains("key")) {
                String[] key_split = pr.substring(6, pr.length() - 1).split("_");
                timestamp_OnImageAvailable = Long.parseLong(key_split[0]);
                pitchDegree = Integer.parseInt(key_split[1]);
            }
            else if(pr.contains("openpose_cnt")) {
                openpose_cnt = Integer.parseInt(pr.replace("openpose_cnt: ", ""));
            }
            else if(pr.contains("openpose_coord")) {
                String[] coord_str = pr.substring("openpose_coord".length() + 3, pr.length() - 1).split(" \\\\n");
                float[][] coord_split = new float[18][3];
                for(int j = 0; j < 18; j++) {
                    String[] joint_coord_str = coord_str[j].split(" ");
                    for(int k = 0; k < 3; k++) {
                        coord_split[j][k] = Float.parseFloat(joint_coord_str[k]);
                    }
                }
                openpose_coordinate.add(coord_split);
            }
            else if(pr.contains("tracker_roi_x"))
            {
                tracker_roi_x = Integer.parseInt(pr.replace("tracker_roi_x: ", ""));
            }
            else if(pr.contains("tracker_roi_y"))
            {
                tracker_roi_y = Integer.parseInt(pr.replace("tracker_roi_y: ", ""));
            }
            else if(pr.contains("tracker_roi_width"))
            {
                tracker_roi_width = Integer.parseInt(pr.replace("tracker_roi_width: ", ""));
            }
            else if(pr.contains("tracker_roi_height"))
            {
                tracker_roi_height = Integer.parseInt(pr.replace("tracker_roi_height: ", ""));
            }
            else if(pr.contains("roi_rectangle_color"))
            {
                roi_rectangle_color = Integer.parseInt(pr.replace("roi_rectangle_color: ", ""));
            }
            else if(pr.contains("yolo_cnt_person")) {
                yolo_cnt_person = Integer.parseInt(pr.replace("yolo_cnt_person: ", ""));
            }
            else if(pr.contains("yolo_cnt_tvmonitor")) {
                yolo_cnt_tvmonitor = Integer.parseInt(pr.replace("yolo_cnt_tvmonitor: ", ""));
                Log.d( "AnalyzedFrame", "yolo_cnt_tvmonitor " + Integer.toString(yolo_cnt_tvmonitor));
            }
            else if(pr.contains("yolo_coord_person")) {
                //2019/4/18: I need to handle multiple person
                String[] coord_str = pr.substring("yolo_coord_person".length() + 3, pr.length() - 3).split(", ");
//                float[] coord_split = new float[4];
//                for(int j = 0; j < 4; j++)
//                    coord_split[j] = Float.parseFloat(coord_str[j]);
                int[] coord_split = new int[4];
                for(int j = 0; j < 4; j++)
                    coord_split[j] = Integer.parseInt(coord_str[j]);
                yolo_coordinate_person.add(coord_split);
            }
            else if(pr.contains("yolo_coord_tvmonitor")) {
                String[] coord_str = pr.substring("yolo_coord_tvmonitor".length() + 3, pr.length() - 3).split(", ");
//                float[] coord_split = new float[4];
//                for(int j = 0; j < 4; j++)
//                    coord_split[j] = Float.parseFloat(coord_str[j]);
                int[] coord_split = new int[4];
                for(int j = 0; j < 4; j++)
                    coord_split[j] = Integer.parseInt(coord_str[j]);
                yolo_coordinate_tvmonitor.add(coord_split);
            }
            else if(pr.contains("charades_webcam")) {
                actions = pr.substring(18, pr.length() - 1).split(";");
            }

        }
        //TODO: Is this criterion proper? Sometimes yolo_cnt = 0 but openpose_cnt > 0.
//        bFoundPerson = (yolo_cnt_person > 0) && (openpose_cnt > 0);
        bFoundPerson = openpose_cnt > 0;

        //Openpose sorts the order of skeletons by size. Thus it is ok to use the first returned skeleton.
        //2019/5/2 Is this still true for Intel's OpenVINO implementation?
        //No, it does not work.
        //I need to do it by myself
        if(openpose_cnt > 1) {
            float dmax = 0;     //the distance of point 0 and 1
            int max_index = 0;
            for( int i = 0 ; i< openpose_cnt ; i++ )
            {
                fMatrix = openpose_coordinate.get(i);
                if( fMatrix[0][2] > 0 && fMatrix[1][2] > 0)
                {
                    float dx = ( fMatrix[0][0] - fMatrix[1][0] );
                    float dy = ( fMatrix[0][1] - fMatrix[1][1] );
                    float dist_square = dx * dx + dy * dy;
                    if( dist_square > dmax)
                    {
                        dmax = dist_square;
                        max_index = i;
                    }
                }
            }
            fMatrix = openpose_coordinate.get(max_index);
        }
        else if( openpose_cnt == 1)
            fMatrix = openpose_coordinate.get(0);
        else
            fMatrix = new float[18][3];

        //TODO: Is the order of yolo returns sorted by size? no.

        //Check the probability, prevent the false positives.
        //If all probability values are less than the threshold, ignore the found person.
        float threshold = 0.4f;
        boolean bExceed = false;
        for(int i=0; i<18; i++)
        {
            if( fMatrix[i][2] > threshold) {
                bExceed = true;
                break;
            }
        }
        if( bExceed)
            bIgnorePerson = false;
        else
            bIgnorePerson =true;

    }
}
