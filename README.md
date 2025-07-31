# Automation Bridge Server

WebSocket/RPC bridge server для связи между AutomationService и AutomationScheduler.

## Описание

Этот сервер работает как посредник между Android приложениями:
- **AutomationService** - выполняет автоматизацию на устройстве
- **AutomationScheduler** - управляет и планирует автоматизацию

## Функции

- WebSocket подключения с автоматическим переподключением
- RPC протокол для вызова методов
- Маршрутизация сообщений между клиентами
- Управление подключенными клиентами
- Логирование и мониторинг

## Требования

- Java 11 или выше
- Maven 3.6+
- Linux Debian 11 (рекомендуется)

## Сборка

```bash
mvn clean package
```

Это создаст файл `target/bridge-server-1.0.0-shaded.jar`

## Запуск

### Простой запуск
```bash
java -jar target/bridge-server-1.0.0-shaded.jar
```

### С параметрами
```bash
java -jar target/bridge-server-1.0.0-shaded.jar --host 0.0.0.0 --port 9090
```

### Использование скриптов
```bash
# Запуск сервера
./start-server.sh [host] [port] [java_opts]

# Остановка сервера
./stop-server.sh
```

## Установка как службы systemd

Для установки как службы Linux:

```bash
sudo ./install-service.sh
```

Затем:
```bash
# Запуск службы
sudo systemctl start automation-bridge-server

# Просмотр статуса
sudo systemctl status automation-bridge-server

# Просмотр логов
sudo journalctl -u automation-bridge-server -f
```

## API

### WebSocket Endpoint
```
ws://host:port/ws
```

### RPC Messages

#### Аутентификация клиента
```json
{
  "id": "uuid",
  "type": "NOTIFICATION",
  "method": "client.authenticate",
  "params": {
    "type": "automation_service|automation_scheduler",
    "name": "ClientName",
    "version": "1.0.0"
  }
}
```

#### Выполнение автоматизации
```json
{
  "id": "uuid",
  "type": "REQUEST",
  "method": "automation.execute",
  "params": {
    "automationId": "automation_id"
  }
}
```

#### Получение статуса
```json
{
  "id": "uuid",
  "type": "REQUEST",
  "method": "automation.get_status"
}
```

### Доступные методы

#### Automation Service
- `automation.get_status` - статус сервиса автоматизации
- `automation.list` - список автоматизаций
- `automation.get` - получить автоматизацию по ID
- `automation.execute` - выполнить автоматизацию
- `vnc.get_status` - статус VNC сервера
- `vnc.start` - запустить VNC сервер
- `vnc.stop` - остановить VNC сервер

#### Server
- `server.status` - статус сервера
- `server.list_clients` - список подключенных клиентов
- `server.ping` - ping/pong

## Конфигурация Android приложений

### AutomationService

Добавьте в манифест:
```xml
<service android:name=".AutomationBridgeService" 
         android:enabled="true" 
         android:exported="false" 
         android:foregroundServiceType="dataSync" />

<receiver android:name=".AutomationBroadcastReceiver">
    <intent-filter>
        <action android:name="com.merged.automationservice.CONNECT_BRIDGE" />
        <action android:name="com.merged.automationservice.DISCONNECT_BRIDGE" />
    </intent-filter>
</receiver>
```

Подключение к серверу:
```java
Intent intent = new Intent("com.merged.automationservice.CONNECT_BRIDGE");
intent.putExtra("bridge_host", "192.168.1.100");
intent.putExtra("bridge_port", 9090);
intent.setPackage("com.merged.automationservice");
sendBroadcast(intent);
```

### AutomationScheduler

Использование клиента:
```java
BridgeWebSocketClient client = new BridgeWebSocketClient(this);
client.connect("192.168.1.100", 9090);

// Выполнение автоматизации
client.executeAutomation("automation_id")
    .thenAccept(result -> {
        // Обработка результата
    });
```

## Логи

Логи сохраняются в:
- Консоль (stdout)
- Файл `logs/automation-bridge-server.log`

## Мониторинг

Сервер предоставляет информацию о:
- Подключенных клиентах
- Статусе соединений
- Обработанных сообщениях

## Безопасность

- Сервер привязан к localhost по умолчанию
- Для удаленных подключений укажите host 0.0.0.0
- Рекомендуется использовать файрвол для ограничения доступа
- В продакшене добавьте аутентификацию и шифрование

## Troubleshooting

### Проблема: "Connection refused"
- Проверьте, что сервер запущен
- Проверьте host и port
- Проверьте файрвол

### Проблема: "WebSocket error"
- Проверьте логи сервера
- Убедитесь в правильности WebSocket URL
- Проверьте сетевое соединение

### Проблема: "RPC timeout"
- Увеличьте таймаут в клиенте
- Проверьте производительность сервера
- Проверьте логи для ошибок

## Разработка

Для разработки:
```bash
# Компиляция
mvn compile

# Запуск в debug режиме
mvn exec:java -Dexec.mainClass="com.merged.automation.bridge.AutomationBridgeServer" -Dexec.args="--host 127.0.0.1 --port 9090"

# Тесты
mvn test
```