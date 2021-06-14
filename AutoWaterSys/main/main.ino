#include <DHT.h>
#include <DHT_U.h>
#include <SoftwareSerial.h>

SoftwareSerial mySerial(2, 3); // RX, TX

#define DHTTYPE DHT11
#define DHTPIN 4
#define PUMP 5

DHT_Unified dht(DHTPIN, DHTTYPE);

sensors_event_t event;

unsigned long old_time = 0;
unsigned long total_time = 0;
bool is_pump_on = false;
bool watering_need = false;
int wait_time = 15000;

float humidity;
float soil_humidity = 0;
float temperature = 0;

float base_humidity = -1;
float top_humidity = -1;

void setup () {
  pinMode(A0, INPUT);
  pinMode(PUMP, OUTPUT);
  Serial.begin(9600);
  mySerial.begin(115200); 
  dht.begin();
}

void loop() {
  soil_humidity = (((- analogRead(A0) + 1023) / 773.0) * 100);
  
  dht.temperature().getEvent(&event);
  if (!isnan(event.temperature)) {
    temperature = event.temperature;
  }
  
  dht.humidity().getEvent(&event);
  if (!isnan(event.relative_humidity)) {
    humidity = event.relative_humidity;
  }

   bt_out();
  
  
  if (mySerial.available()) {
    String bt_in = mySerial.readStringUntil('\n');
    mySerial.flush();
    char bt_command = bt_in[0];
    
    Serial.print("REC: ");
    Serial.println(bt_in);

//    Serial.print("B: ");
//    Serial.println(bt_in.substring(5, 8).toFloat());
    
    switch (bt_command) {
      case 'A':
        base_humidity = bt_in.substring(1, 4).toFloat();
        top_humidity = bt_in.substring(4, 7).toFloat();
        break;
      case 'P':
        watering_need = true;
        break;
    }
  }
  controller();
}

void bt_out() {
  mySerial.print("S");
  mySerial.println(soil_humidity);
  
  mySerial.print("T");
  mySerial.println(temperature);
  
  mySerial.print("H");
  mySerial.println(humidity);

  mySerial.print("P");
  mySerial.println(is_pump_on);

  delay(20);
//  Serial.print("LOW:");
//  Serial.print(base_humidity);
//  Serial.print(" HIGH:");
//  Serial.println(top_humidity);
//  Serial.print("P on: ");
//  Serial.println(is_pump_on);
//  Serial.print("Time: ");
//  Serial.println(total_time);
}

void controller() {
  unsigned long delta_time = calculate_deltatime();
  total_time += delta_time;

  if (base_humidity > -1) {
    if (soil_humidity < base_humidity) {
      watering_need = true;
    }

    if (soil_humidity > top_humidity) {
      watering_need = false;
    }
  
    if (watering_need) {
      if (!is_pump_on) {
        if (total_time > wait_time) {
          pump_on();
          total_time = 0;
        }
      } else {
        if (total_time > 3000) {
          pump_off();
          total_time = 0;
        }
      }
    }
  }
}

unsigned long calculate_deltatime() {
  unsigned long current_time = millis();
  unsigned long delta_time = current_time - old_time;
  old_time = current_time;
  return delta_time;
}

void pump_on() {
  digitalWrite(PUMP, HIGH);
  is_pump_on = true;
}

void pump_off () {
  digitalWrite(PUMP, LOW);
  is_pump_on = false;
}
