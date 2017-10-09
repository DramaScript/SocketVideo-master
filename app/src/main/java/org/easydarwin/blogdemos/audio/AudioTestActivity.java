package org.easydarwin.blogdemos.audio;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.easydarwin.blogdemos.R;

/**
 * @CreadBy ：DramaScript
 * @date 2017/9/1
 */
public class AudioTestActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btn_start,btn_end,btn_play;
    private TextView tv_result;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_end = (Button) findViewById(R.id.btn_end);
        btn_play = (Button) findViewById(R.id.btn_play);
        tv_result = (TextView) findViewById(R.id.tv_result);

        btn_start.setOnClickListener(this);
        btn_end.setOnClickListener(this);
        btn_play.setOnClickListener(this);


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_start:

                break;
            case R.id.btn_end:

                break;
            case R.id.btn_play:

                break;
        }
    }
}
