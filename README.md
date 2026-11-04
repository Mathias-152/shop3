# Shop

Aplikacja Spring Boot z klientami, zamówieniami i historią zdarzeń odbieranych z Kafki.
Java 21, H2, Spring Kafka, Groovy i Spock.

[Zadania — Kafka](docs/zadania_na_lab.md)

## Uruchomienie

Ustaw `JAVA_HOME` na katalog JDK 21. Przy pierwszym uruchomieniu Gradle pobierze
zależności z internetu.

```powershell
.\gradlew.bat bootLocal
```

Aplikacja: `http://localhost:8080`. Zadanie uruchamia również broker Kafka na losowym
porcie. Nie trzeba instalować Kafki ani Dockera. Zatrzymanie: Ctrl+C.

Na Linux/macOS użyj `./gradlew bootLocal`. W IntelliJ wybierz konfigurację
`Shop lokalnie` albo uruchom `LocalShopApplication.main` z katalogu `src/test/java`.

Baza H2 i lokalny broker są tymczasowe. Po ponownym uruchomieniu aplikacji utwórz
klienta i zamówienia od nowa. `ShopApplication` i `bootRun` wymagają osobnego brokera
pod adresem `localhost:9092` lub wskazanym przez `KAFKA_BOOTSTRAP_SERVERS`.

## API

Przykładowe żądania są w [orders.http](orders.http). Zacznij od utworzenia klienta,
następnie użyj jego identyfikatora w zamówieniu.

Bez klienta HTTP w IDE użyj [orders.ps1](orders.ps1). Przy działającej aplikacji
wykonaj w drugim terminalu PowerShell, z katalogu projektu:

```powershell
powershell -ExecutionPolicy Bypass -File .\orders.ps1
```

Skrypt tworzy klienta i zamówienie oraz odczytuje historię. Wypisuje też komendę
do ponownego odczytu historii, która początkowo może być pusta. Każde uruchomienie
tworzy nowego klienta i zamówienie. Inny adres aplikacji podaj przez `-BaseUrl`.

```json
{"customerId":1,"productName":"Książka","quantity":2,"unitPrice":19.99}
```

| Adres | Operacje |
| --- | --- |
| `/customers` | POST z polem `name`, GET listy. |
| `/customers/{id}` | GET, PUT, DELETE. |
| `/orders` | POST zamówienia, GET listy. |
| `/orders/{id}` | GET, PUT, DELETE. |
| `/audit/orders/{orderId}` | GET historii utworzenia zamówienia. |

Zamówienie wymaga istniejącego klienta. Nazwy mają do 100 znaków, liczba sztuk
i cena muszą być dodatnie. Cena ma maksymalnie 2 miejsca po przecinku i wartość
9999999999.99. Nie można usunąć klienta mającego zamówienia — API zwróci 409.

## Kafka

| Ustawienie | Wartość |
| --- | --- |
| Topic | `shop.order-created.v1` |
| Partycje | 3 |
| Repliki | 1 |
| Klucz wiadomości | `orderId` jako tekst |
| Wartość wiadomości | JSON zgodny z `OrderCreated` |
| Grupa audytu | `shop-audit-v1` |
| Zatwierdzanie offsetów | `ack-mode=record`, bez auto-commit |
| Początek odczytu bez zapisanego offsetu | `earliest` |

Konfiguracja: `application.properties` i `KafkaConfiguration`.
Producent: `OutboxPublisher`. Konsument: `OrderCreatedListener`.

Przy tworzeniu zamówienia serwis zapisuje również zdarzenie w tabeli
`outbox_messages`. Publikator wysyła je do Kafki i usuwa z outboxa po potwierdzeniu
publikacji. Nieudana próba zostawia rekord do ponowienia.

Audyt zapisuje odebrane dane w `audit_entries`. Historia może pojawić się później
niż odpowiedź HTTP. Powtórzona wiadomość z tym samym `eventId` nie tworzy drugiego
wpisu. PUT i DELETE zamówienia nie publikują `OrderCreated` i nie zmieniają historii.

W tej wersji działa jeden publikator outboxa. Błędy przetwarzania są ponawiane;
nie ma osobnego topicu na odrzucone wiadomości (DLT).

## Testy

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests '*KafkaBasicsSpec'
.\gradlew.bat test --tests '*OrderEventsIntegrationSpec'
```

`KafkaBasicsSpec` sprawdza klucze, partycje, grupy konsumentów i offsety.
`OrderEventsIntegrationSpec` sprawdza publikację zamówień, zatrzymanie konsumenta,
ponowienia i duplikaty. Pozostałe testy dotyczą klientów, zamówień i zapisu w H2.

Testy uruchamiają własny broker przez
[Embedded Kafka](https://docs.spring.io/spring-kafka/reference/testing.html).
Nie korzystają z brokera ani bazy uruchomionych przez `bootLocal`.
W testach integracyjnych harmonogram outboxa jest wyłączony; publikację uruchamia
`publisher.publishPending()`.

Raport: `build/reports/tests/test/index.html`.

## Baza

Konsola: `http://localhost:8080/h2-console`.
JDBC URL: `jdbc:h2:mem:shop`, użytkownik: `sa`, hasło puste.

Tabele: `customers`, `orders`, `outbox_messages`, `audit_entries`.

Opis ustawień konsumenta:
[Apache Kafka — Consumer Configs](https://kafka.apache.org/42/configuration/consumer-configs/).
