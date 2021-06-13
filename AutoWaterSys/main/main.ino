#include <DHT.h>
#include <DHT_U.h>

#define DHTTYPE DHT11
#define DHTPIN 2
#define PUMP 3

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

char bt_command;
float bt_command_data;

float base_humidity = -1;
float top_humidity = -1;

void setup () {
  pinMode(A0, INPUT);
  pinMode(PUMP, OUTPUT);
  Serial.begin(9600);
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
  
  if (Serial.available() > 0) {
    String bt_in = Serial.readString();
    bt_command = bt_in[0];
    
    bt_command_data = bt_in.substring(1).toFloat();

    switch (bt_command) {
      case 'A':
        base_humidity = bt_command_data;
        break;
      case 'B':
        top_humidity = bt_command_data;
        break;
      case 'P':
        watering_need = true;
        break;
    }
  }

  controller();
}

void bt_out() {
  Serial.print("S");
  Serial.println(soil_humidity);
  
  Serial.print("T");
  Serial.println(temperature);
  
  Serial.print("H");
  Serial.println(humidity);

  Serial.print("P");
  Serial.println(is_pump_on);

  Serial.print("TIME:");
  Serial.println(total_time);
}

void controller() {
  unsigned long delta_time = calculate_deltatime();
  total_time += delta_time;

  if (base_humidity > -1 and top_humidity > -1) {
    if (soil_humidity < base_humidity) {
      watering_need = true;
    }

    if (soil_humidity > top_humidity) {
      watering_need = false;
    }
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
