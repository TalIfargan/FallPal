
#include "BluetoothSerial.h"
#include <Adafruit_LSM303_Accel.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

Adafruit_LSM303_Accel_Unified accel = Adafruit_LSM303_Accel_Unified(54321);
BluetoothSerial SerialBT;
double norm = 0.0;
int sampleNum = 5;
double x = 0;
double y = 0;
double z = 0;



void setup()
{
  Serial.begin(115200);
  SerialBT.begin("FallPal Device"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  #ifndef ESP8266
    while (!Serial)
      ; // will pause Zero, Leonardo, etc until serial console opens
  #endif
    /* Initialise the sensor */
    if (!accel.begin()) {
      /* There was a problem detecting the ADXL345 ... check your connections */
      Serial.println("Ooops, no LSM303 detected ... Check your wiring!");
      while (1)
        ;
    }
  
    accel.setRange(LSM303_RANGE_4G);
    Serial.print("Range set to: ");
    lsm303_accel_range_t new_range = accel.getRange();
    switch (new_range) {
    case LSM303_RANGE_2G:
      Serial.println("+- 2G");
      break;
    case LSM303_RANGE_4G:
      Serial.println("+- 4G");
      break;
    case LSM303_RANGE_8G:
      Serial.println("+- 8G");
      break;
    case LSM303_RANGE_16G:
      Serial.println("+- 16G");
      break;
    }
  
    accel.setMode(LSM303_MODE_NORMAL);
    Serial.print("Mode set to: ");
    lsm303_accel_mode_t new_mode = accel.getMode();
    switch (new_mode) {
    case LSM303_MODE_NORMAL:
      Serial.println("Normal");
      break;
    case LSM303_MODE_LOW_POWER:
      Serial.println("Low Power");
      break;
    case LSM303_MODE_HIGH_RESOLUTION:
      Serial.println("High Resolution");
      break;
    }
  

}

void loop(void)
{
  /* Display the results (acceleration is measured in m/s^2) */
 
  x = 0;
  y = 0;
  z = 0;
  norm = 0;
  for (int i = 0; i < sampleNum; i++) {
      /* Get a new sensor event */
      sensors_event_t event;
      accel.getEvent(&event);
      double newx =  event.acceleration.x;
      double newy =  event.acceleration.y;
      double newz =  event.acceleration.z;
      x += newx;
      y += newy;
      z += newz;
      norm += sqrt(newx*newx + newy*newy + newz*newz);
  }
  x = x/sampleNum;
  y = y/sampleNum;
  z = z/sampleNum;
  norm = norm/sampleNum;
  
  SerialBT.println((String) norm);

}
