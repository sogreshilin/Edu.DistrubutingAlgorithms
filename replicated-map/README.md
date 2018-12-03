# Реплицируемый ассоциативный массив

## Общее описание

В этой лабораторной работе реализован следующий интерфейс:

```java
interface Map {
    boolean remove(String key);
    boolean put(String key, Double value);
    Optional<Double> get(String key);
    boolean compareAndSwap(String key, Double oldValue, Double newValue);
}
``` 

Актуальная копия мапы в каждый момент времени должна быть на каждом узле кластера. 
Это достигается за счет использования межкластерного мьютекса.

## Как собрать проект

Проект собирается `gradle`ом. Для сборки выполнить:

```
./gradlew build
```

Для запуска узла кластера выполнить:

```
./lab02-map-start.sh
```