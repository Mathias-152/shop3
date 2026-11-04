## 1. Zamówienie i zdarzenie

Aplikacja zapisuje zamówienia w bazie. Osobno prowadzi audyt, czyli historię
utworzenia zamówień. Audyt powstaje po odebraniu wiadomości z Kafki. Sprawdzisz,
które fragmenty kodu odpowiadają za zapis zamówienia, publikację i odbiór wiadomości.

Dane do ćwiczeń twórz przez adresy GET otwierane w przeglądarce. Są dostępne przy
uruchomieniu `Shop lokalnie` (`bootLocal`) lub `LocalShopApplication`.

1. Otwórz [utworzenie klienta](http://localhost:8080/lab/create-customer).
   Otrzymasz JSON z polami `id` i `name`. Zapamiętaj `id`.
2. Otwórz [utworzenie zamówienia](http://localhost:8080/lab/create-orders?customerId=1&count=1).
   W adresie zastąp `customerId=1` identyfikatorem swojego klienta.
   Odpowiedź zawiera listę utworzonych zamówień. Zapamiętaj `id` jednego z nich.
3. Odczytaj zamówienie pod adresem `http://localhost:8080/orders/1`, a jego historię
   pod `http://localhost:8080/audit/orders/1`. W obu adresach podstaw `id` zamówienia.
   Jeśli historia jest pusta (`[]`), odśwież stronę po chwili.

Parametr `count` określa liczbę zamówień do utworzenia i przyjmuje wartości 1–20.
Każde otwarcie lub odświeżenie adresu `/lab/create-orders` tworzy kolejne zamówienia.
Do ponownego odczytu istniejących danych służą `/orders/{id}` i `/audit/orders/{id}`.
Po restarcie lokalnej aplikacji utwórz klienta ponownie — baza i broker są tymczasowe.

Broker to proces Kafki, który przyjmuje i przechowuje wiadomości. Uruchamia go
`LocalShopApplication`. Producent wysyła wiadomości do nazwanego topicu, a konsument
je odczytuje. Topic jest podzielony na partycje; każda przechowuje uporządkowany
ciąg wiadomości. Konfigurację i obsługę wiadomości znajdziesz w następujących miejscach:

| Plik | Co sprawdzić |
| --- | --- |
| [application.properties](../src/main/resources/application.properties) | `shop.kafka.order-created.topic` i `shop.kafka.order-created.partitions`: nazwa topicu i liczba partycji. |
| [KafkaConfiguration](../src/main/java/edu/arch/shop/messaging/KafkaConfiguration.java) | Bean `NewTopic`: na jego podstawie Spring tworzy topic o podanej nazwie i liczbie partycji. |
| [OrderService](../src/main/java/edu/arch/shop/order/service/OrderService.java) | Metoda `create`: zapis zamówienia i utworzenie zdarzenia. |
| [EventOutbox](../src/main/java/edu/arch/shop/messaging/outbox/EventOutbox.java) | Przygotowanie klucza i JSON-a wiadomości do późniejszej wysyłki. |
| [OutboxPublisher](../src/main/java/edu/arch/shop/messaging/outbox/OutboxPublisher.java) | Wywołanie `KafkaTemplate.send(topic, key, value)` i oczekiwanie na wynik publikacji. |
| [OrderCreatedListener](../src/main/java/edu/arch/shop/audit/messaging/OrderCreatedListener.java) | Metoda z `@KafkaListener`, która odbiera wiadomość i przekazuje ją do audytu. |
| [OrderCreated](../src/main/java/edu/arch/shop/contracts/OrderCreated.java) | Dane zdarzenia, które producent zapisuje jako JSON, a konsument odczytuje. |

Przejrzyj wskazane metody. Ustal, skąd pochodzi klucz wiadomości i jakie dane
znajdują się w jej wartości. W `OutboxPublisher` sprawdź, kiedy znika rekord
z tabeli `outbox_messages`.

Odpowiedz na podstawie kodu: czy odpowiedź z utworzonym zamówieniem oznacza już
zapis w audycie? A czy oznacza go potwierdzenie publikacji uzyskane przez `send(...).get(...)`?
Wskaż, na co czeka każde z tych wywołań.

## 2. Klucz, partycja i offset

Obecnie metoda `OrderCreatedListener.onOrderCreated` przyjmuje `String payload`.
Jest to wyłącznie treść wiadomości, czyli JSON opisujący zdarzenie. Do obserwowania
pracy Kafki potrzebujesz także informacji o tym, skąd wiadomość została odczytana.

Spring może przekazać metodzie obiekt
[ConsumerRecord<K, V>](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html).
`K` oznacza typ klucza, a `V` typ wartości. W tej aplikacji oba są tekstem,
dlatego użyjesz `ConsumerRecord<String, String>`.

| Wywołanie | Co otrzymasz |
| --- | --- |
| `record.value()` | Ten sam JSON, który dotychczas trafiał do `payload`. |
| `record.key()` | Klucz nadany przez producenta; tutaj identyfikator zamówienia zapisany jako tekst. |
| `record.topic()` | Nazwę topicu, z którego pochodzi wiadomość. |
| `record.partition()` | Numer partycji, w której zapisano wiadomość. |
| `record.offset()` | Pozycję wiadomości w tej partycji. |

Wykonaj następujące zmiany:

1. Zaimportuj `org.apache.kafka.clients.consumer.ConsumerRecord`. Zmień argument
   metody na `ConsumerRecord<String, String> record`.
2. Przy deserializacji do `OrderCreated` użyj `record.value()` zamiast `payload`.
   Nadal przekazuj odczytane zdarzenie do `auditService.record`.
3. Dodaj log z nazwą topicu, kluczem, partycją i offsetem. Przykład użycia loggera
   SLF4J znajdziesz w `OutboxPublisher`. Wpis powinien pozwalać odczytać wszystkie
   cztery wartości, np. `topic=shop.order-created.v1 key=7 partition=2 offset=0`.
4. Uruchom aplikację ponownie i otwórz
   [utworzenie klienta](http://localhost:8080/lab/create-customer).
   Następnie otwórz
   [utworzenie pięciu zamówień](http://localhost:8080/lab/create-orders?customerId=1&count=5),
   podstawiając właściwy `customerId`. Porównaj identyfikatory z odpowiedzi
   przeglądarki z kluczami w logach. Zapisz partycję i offset kilku wiadomości.

Każde nowe zamówienie ma inny identyfikator, a więc także inny klucz. Żeby zobaczyć,
co dzieje się z kilkoma wiadomościami o **tym samym kluczu**, otwórz
[KafkaBasicsSpec](../src/test/groovy/edu/arch/shop/messaging/KafkaBasicsSpec.groovy).
Uruchom test „wiadomości z tym samym kluczem trafiają do jednej partycji”.
Wysyła on wartości `first`, `second` i `third` z kluczem `order-42`.

Dodaj wartość `fourth`. Dostosuj liczbę oczekiwanych wiadomości w `receive`
oraz asercje dotyczące wartości i offsetów. Następnie sprawdź wariant z różnymi
kluczami i obejrzyj przypisane partycje. Różne klucze mogą trafić do tej samej partycji.

Wyjaśnij na podstawie wyników:

- Czy dwie różne wiadomości mogą mieć offset `0`? Jak rozpoznasz wtedy każdą z nich?
- Czy offsety `10` i `20` z różnych partycji pozwalają ustalić kolejność wysłania?
- Gdyby zależało Ci na kolejności zdarzeń dotyczących jednego klienta, jaki klucz
  wybrałbyś dla takich wiadomości?

## 3. Drugi konsument i grupy

Ten sam topic może być czytany przez kilka części aplikacji, np. audyt i obsługę
powiadomień. Grupa konsumentów określa, którzy konsumenci współpracują przy odczycie.
W ramach jednej grupy dana partycja jest w danej chwili przypisana jednemu
konsumentowi. Różne grupy odczytują topic niezależnie i mają własne zatwierdzone
offsety. Opis tego mechanizmu znajdziesz w
[dokumentacji konsumenta](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html).

Sprawdzisz dwa przypadki: dwa niezależne zastosowania tej samej wiadomości
oraz podział pracy we wspólnej grupie.

1. Dodaj klasę `OrderNotificationListener` w pakiecie
   `edu.arch.shop.notification.messaging`. Oznacz ją `@Component`.
2. Dodaj metodę z `@KafkaListener`, przyjmującą `ConsumerRecord<String, String>`.
   Użyj `topics = "${shop.kafka.order-created.topic}"`, `id = "notifications"`
   oraz `groupId = "shop-notifications-v1"`.
3. Odczytaj `OrderCreated` z `record.value()`. Wypisz `orderId`, `eventId`, partycję
   i offset. W tym ćwiczeniu zapis do logu jest całą obsługą powiadomienia.
4. Uruchom aplikację ponownie. Utwórz klienta przez
   [adres GET](http://localhost:8080/lab/create-customer), a potem
   [dziesięć zamówień](http://localhost:8080/lab/create-orders?customerId=1&count=10)
   dla otrzymanego `customerId`. Sprawdź, czy wiadomości o tych samych
   identyfikatorach pojawiły się w logach obu listenerów i czy powstały wpisy w audycie.
5. Zmień **tylko `groupId`** powiadomień na `shop-audit-v1`. Zostaw inne `id`
   dla każdego listenera. Po restarcie ponownie utwórz klienta i dziesięć zamówień
   przez te same adresy.
6. W logach znajdź wpisy `partitions assigned` oraz własne wpisy obu listenerów.
   Porównaj ich partycje. Dla kilku zamówień odczytaj
   `http://localhost:8080/audit/orders/1`, podstawiając odpowiednie `id`.
   Jeśli próbka nie obejmuje wszystkich partycji, otwórz ponownie adres tworzący zamówienia.

Przy jawnie podanym `groupId`, jak w tym ćwiczeniu, `id` identyfikuje kontener
listenera w Springu, a `groupId` ustala jego grupę w Kafce. Te ustawienia opisuje
[dokumentacja @KafkaListener](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html#annotation-properties).

Uruchom też test „konsumenci z różnych grup otrzymują to samo zdarzenie” w
[OrderEventsIntegrationSpec](../src/test/groovy/edu/arch/shop/messaging/OrderEventsIntegrationSpec.groovy).
Zobacz, jak powstają dwie różne grupy i jak test porównuje odebrane wiadomości.
Test korzysta z osobnego topicu i czterech partycji ustawionych w `IntegrationSpec`.
Liczba partycji w aplikacji lokalnej pochodzi z `application.properties`.

Wyjaśnij, dlaczego po połączeniu listenerów w jedną grupę część zamówień może
nie mieć wpisu w audycie. Czy jest to utrata wiadomości przez Kafkę?
Ilu konsumentów jednej grupy może jednocześnie odczytywać topic o trzech partycjach?
Które ustawienie grup wybierzesz, jeśli każde zdarzenie ma zarówno trafić do audytu,
jak i wywołać powiadomienie?

Na koniec przywróć powiadomieniom grupę `shop-notifications-v1`.

## 4. Offsety, earliest i latest

Konsument ma bieżącą pozycję odczytu. Osobno może zatwierdzić offset, czyli zapisać
w Kafce pozycję, od której jego grupa ma wznowić pracę. Zatwierdzony offset dotyczy
konkretnej grupy, topicu i partycji oraz wskazuje następną pozycję do odczytu.
Odebranie wiadomości i zatwierdzenie offsetu to dwa osobne działania.

Ustawienie
[auto.offset.reset](https://kafka.apache.org/42/configuration/consumer-configs/#auto.offset.reset)
jest używane, gdy dla danej partycji nie ma prawidłowego zapisanego offsetu:

- `earliest` rozpoczyna od najstarszych dostępnych wiadomości;
- `latest` ustala pozycję na końcu partycji; odczyt obejmie wiadomości dopisane później.

W `KafkaBasicsSpec` konsumenci mają wyłączone automatyczne zatwierdzanie offsetów.
Metoda `commitSync()` zapisuje pozycję jawnie. Sprawdź to w pomocniczej metodzie
`newConsumer` i w teście „konsument z tą samą grupą wznawia odczyt od zatwierdzonego offsetu”.

1. Uruchom ten test. Znajdź odczyt `first`, wywołanie `commitSync()`, zamknięcie
   pierwszego konsumenta i utworzenie kolejnego z tym samym `groupId`.
2. Ustaw wznowionemu konsumentowi `latest` zamiast `earliest`. Uruchom test ponownie.
   Ustal, czy o miejscu wznowienia zdecydowała ta zmiana, czy zapisany offset.
3. Przywróć `earliest` i usuń na chwilę `commitSync()`. Przed uruchomieniem przewidź,
   która wiadomość zostanie odczytana ponownie. Porównaj przewidywanie z wynikiem,
   a następnie przywróć commit i działający test.
4. Uruchom test „nowa grupa z earliest odczytuje wcześniejsze wiadomości, a z latest tylko nowe”.
   Sprawdź, dlaczego `after` jest wysyłane dopiero po przydzieleniu partycji
   konsumentowi `latest` i ustaleniu jego pozycji przez `position()`.

Dopisz test z nową grupą: opublikuj dwie wiadomości, odczytaj je pierwszym
konsumentem i zatwierdź offset. Zamknij go. Utwórz drugiego konsumenta z innym
`groupId` oraz `earliest`. Sprawdź, czy odczyta obie wiadomości w kolejności wysłania.
Skorzystaj z `newTopic(1)`, `newConsumer` i `receive`. Zamknij obu konsumentów w `cleanup`.

Na podstawie tych eksperymentów wyjaśnij, dlaczego zmiana `earliest` na `latest`
nie jest sposobem na pominięcie zaległości grupy mającej zapisane offsety.
Dlaczego nowa grupa może odczytać wiadomości już obsłużone przez inną grupę?

## 5. Zatrzymany konsument i nieudana publikacja

Producent i konsument mogą działać niezależnie. Sprawdzisz, gdzie pozostaje
wiadomość w dwóch sytuacjach: gdy nie działa odbiorca oraz gdy nie można jej wysłać.
Pomogą w tym istniejące testy w `OrderEventsIntegrationSpec`.

Tabela `outbox_messages` przechowuje zdarzenia oczekujące na publikację.
W testach harmonogram publikacji jest wyłączony, więc wysyłkę uruchamia jawne
wywołanie `publisher.publishPending()`. Pozwala to sprawdzić stan danych przed
publikacją i po niej.

1. Uruchom test „po wznowieniu konsument przetwarza zdarzenie wysłane podczas jego zatrzymania”.
   `registry.getListenerContainer("audit-orders")` pobiera kontener listenera.
   `stop()` zatrzymuje odbiór, a `start()` go wznawia; broker cały czas działa.
2. Sprawdź asercje po `publishPending()`, ale przed `listener.start()`.
   Zanotuj, czy istnieje zamówienie, czy zdarzenie jest jeszcze w outboxie i czy
   istnieje wpis w audycie. Ustal, gdzie czeka wtedy wiadomość.
3. Rozszerz test o drugie zamówienie utworzone przy zatrzymanym konsumencie.
   Po publikacji sprawdź brak historii obu zamówień, a po wznowieniu konsumenta
   sprawdź, że każde ma dokładnie jeden wpis w audycie.
4. Do oczekiwania użyj istniejącego `eventually.eventually { ... }` z asercjami
   wewnątrz bloku. `PollingConditions` powtarza sprawdzenie do spełnienia warunku
   lub przekroczenia limitu. Dzięki temu test uwzględnia asynchroniczny odbiór.
5. Uruchom test „po błędzie publikacji zdarzenie pozostaje w outboxie i zostaje wysłane przy ponowieniu”.
   Znajdź producenta skierowanego na port, na którym nie działa Kafka. Porównaj
   stan outboxa i audytu po błędzie oraz po ponowieniu przez działającego producenta.

Testy uruchamiają własny broker, niezależny od `bootLocal`. Nie zastępuj zatrzymania
listenera restartem całej aplikacji lokalnej: taki restart usuwa również jej
tymczasową bazę i dane lokalnego brokera.

Porównaj oba przypadki. W którym ponowienie wysyłki jest potrzebne, a w którym
wystarczy wznowienie odbioru? Dlaczego usunięcie rekordu z outboxa przed
potwierdzeniem publikacji grozi utratą zdarzenia?

## 6. Powtórzona wiadomość

Obsługa wiadomości może zakończyć się sukcesem, a aplikacja może ulec awarii przed
zatwierdzeniem offsetu. Po wznowieniu odczytu ten sam rekord może dotrzeć ponownie.
Możliwe jest też ponowne opublikowanie tego samego zdarzenia, np. po awarii
publikatora przed usunięciem go z outboxa.

Konsument powinien obsługiwać takie powtórzenia bez ponawiania skutku biznesowego.
W tym projekcie oznacza to jeden wpis w audycie dla jednego `eventId`.
Taka właściwość obsługi nazywa się idempotencją.

1. Uruchom test „ponowny odbiór zdarzenia z tym samym eventId nie dodaje wpisu w audycie”.
   Odszukaj drugie wywołanie `send`: publikuje ten sam klucz i ten sam JSON.
   Sprawdź, że `eventId` w ponownie wysłanym JSON-ie się nie zmienia. Odczytaj
   partycję i offset z `duplicate.recordMetadata`. Ponowne opublikowanie tworzy
   kolejny rekord w Kafce, choć opisuje to samo zdarzenie.
2. Otwórz [AuditService](../src/main/java/edu/arch/shop/audit/service/AuditService.java)
   i [AuditEntry](../src/main/java/edu/arch/shop/audit/entity/AuditEntry.java).
   Sprawdź, jakie pole służy do wykrycia powtórzenia i jest kluczem głównym wpisu.
3. Znajdź wywołanie `awaitCommitted`. Test czeka na zatwierdzenie offsetu drugiej
   wiadomości przed sprawdzeniem liczby wpisów. Wyjaśnij, dlaczego sprawdzenie
   `count() == 1` od razu po wysłaniu duplikatu mogłoby przejść, mimo błędnego konsumenta.
4. Dopisz test, w którym celowo tworzysz drugie `OrderCreated` z tym samym `orderId`,
   lecz nowym `eventId` uzyskanym przez `UUID.randomUUID()`. Pozostałe dane pozostaw
   takie same. Zserializuj zdarzenie przez istniejący `objectMapper` i opublikuj je
   na `orderCreatedTopic`, używając tego samego klucza zamówienia.
5. Zaczekaj na przetworzenie i sprawdź dwa wpisy w audycie: ten sam `orderId`,
   ale dwa różne `eventId`. Porównaj wynik z testem ponownej publikacji bez zmiany `eventId`.

Wyjaśnij różnicę między `orderId`, `eventId` i pozycją rekordu określoną przez
topic, partycję i offset. Dlaczego wykrywanie duplikatów tylko po `orderId`
mogłoby odrzucać kolejne, różne zdarzenia dotyczące tego samego zamówienia?

## Zakończenie

Przywróć powiadomieniom własną grupę i cofnij tymczasowe zmiany ustawień offsetów.
Pozostaw logowanie metadanych, nowy listener oraz dopisane testy. Uruchom:

```powershell
.\gradlew.bat test
```

Zapisz zmiany w commitach. W opisie rozwiązania odnieś się do wyników swoich
eksperymentów: podaj przykład podziału pracy w grupie, wznowienia odczytu
i ponownej obsługi zdarzenia. Wyjaśnij, jakie ustawienie lub fragment kodu
decydował o zachowaniu w każdym z tych przypadków.
