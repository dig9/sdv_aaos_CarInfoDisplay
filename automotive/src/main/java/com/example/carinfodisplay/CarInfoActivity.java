package com.example.carinfodisplay;

import android.app.Activity;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Locale;

public class CarInfoActivity extends Activity { // AppCompatActivity 에서 Activity로 변경
    private Car car;
    private CarPropertyManager propertyManager;
    private final String TAG = "CarInfoActivity";
    private TextView speedView, gearView, tempView;
    private int defaultColorSpeed;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_info);

        speedView = findViewById(R.id.text_speed);
        gearView = findViewById(R.id.text_gear);
        tempView = findViewById(R.id.text_temp);
        defaultColorSpeed = speedView.getCurrentTextColor();

        Handler handler = new Handler(Looper.getMainLooper());
        car = Car.createCar(this, handler, 0, new Car.CarServiceLifecycleListener() {
            @Override
            public void onLifecycleChanged(@NonNull Car readyCar, boolean ready) {
                if (ready) {
                    CarInfoActivity.this.car = readyCar;
                    initCar();
                } else {
                    Log.w(TAG, "Car service not ready");
                }
            }
        });
    }

    private void initCar() {
        propertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
        if (propertyManager == null) {
            Log.e(TAG, "CarPropertyManager unavailable");
            return;
        }
        propertyManager.registerCallback(callback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL);
        propertyManager.registerCallback(callback,
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        propertyManager.registerCallback(callback,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
    }

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue carPropertyValue) {
                    switch (carPropertyValue.getPropertyId()) {
                        case VehiclePropertyIds.PERF_VEHICLE_SPEED:
                            //실제 주행 속도가 m/sec 로 전달, km/h 로 변환
                            float kmh = ((Float) carPropertyValue.getValue()) * 3.6f;
                            runOnUiThread(() -> {
                                //속도 표시 및 과속에 따른 색상 변경
                                speedView.setText(
                                        String.format(Locale.US, "%.1f km/h", kmh));
                                speedView.setTextColor(kmh > 110 ?
                                        0xffff0000 : defaultColorSpeed);
                            });
                            break;
                        case VehiclePropertyIds.GEAR_SELECTION:
                            int gear = (Integer) carPropertyValue.getValue();
                            runOnUiThread(() -> gearView.setText(gearToString(gear)));
                            break;
                        case VehiclePropertyIds.HVAC_TEMPERATURE_SET:
                            float t = (Float) carPropertyValue.getValue();
                            int area = carPropertyValue.getAreaId();
                            String seat = decodeSeat(area); //area id 에 의한 좌석 구분
                            runOnUiThread(() ->
                                    tempView.setText(String.format(Locale.US,
                                            "%s / %.1f", seat, t)));
                            break;
                    }

                }
                @Override
                public void onErrorEvent(int i, int i1) {
                }
            };

    private String gearToString(int gear) {
        switch (gear) {
            case 0:
                return "Unknown";
            case 1:
                return "N";
            case 2:
                return "R";
            case 4:
                return "P";
            case 8:
                return "D";
            default:
                if ((0 < gear / 16) && (gear % 16 == 0)) {
                    return String.format(Locale.getDefault(), "Manual-%d", gear / 16);
                }
                return String.format(Locale.getDefault(), "Unknown (%d)", gear);
        }
    }

    private static String decodeSeat(int area) {
        /*
         * cuttlefish·goldfish 에뮬레이터 규칙:
         *   areaIds[0] == 0x20 → 운전석
         *   areaIds[1] == 0x40 → 동승석
         * 그렇지 않은 경우(실차·OEM)에는 사용자가 직접 매핑 표를 바꿔야 한다.
         */
        if (area == 0x20) return "DRIVER";
        if (area == 0x40) return "ASSIST";
        return "OTHER";
    }
}
