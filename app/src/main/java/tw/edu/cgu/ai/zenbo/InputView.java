/*  This file has been created by Nataniel Ruiz affiliated with Wall Lab
 *  at the Georgia Institute of Technology School of Interactive Computing
 */

package tw.edu.cgu.ai.zenbo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;


public class InputView extends View {
    private Bitmap DisplayBitmap = null;

    public InputView(final Context context, final AttributeSet set) {
        super(context, set);

    }

    public void setBitmap( final Bitmap NewBitmap) {
//        Matrix matrix = new Matrix();
//        matrix.postScale(-1, 1, 240, 320);
//        DisplayBitmap = Bitmap.createBitmap(NewBitmap,0,0,NewBitmap.getWidth(),NewBitmap.getHeight(),matrix,true);
        DisplayBitmap = NewBitmap;
    }

    @Override
    public void onDraw(final Canvas canvas) {
        if( DisplayBitmap != null) {
            canvas.drawBitmap(DisplayBitmap, 0, 0, null);
        }
    }
}
