# ⛴️ Ferry Transport Simulation (Java)

A concurrent Java desktop simulation designed to model and coordinate vehicle boarding, loading, and transit operations for 30 vehicle threads with strict synchronization mechanisms and a live GUI.

## 📌 Project Overview
This project tackles synchronization challenges in multithreaded environments. It coordinates multiple vehicle threads competing for limited ferry capacity, ensuring system fairness and preventing common concurrency pitfalls.

## ⚙️ Concurrency & Synchronization Mechanisms
- **Fair Locks:** Ensures strict ordering of threads requesting access.
- **Semaphores & Condition Variables:** Manages resource allocation and precise thread signaling during boarding states.
- **Deadlock & Starvation Avoidance:** Implements robust scheduling logic to guarantee FIFO fairness and prevent thread starvation under heavy load.

## 🖥️ Graphical User Interface (GUI)
- Built using **Java Swing** for real-time visualization.
- Displays live status updates of vehicle threads, ferry movement, and dock capacity.

## 💻 Tech Stack
- **Language:** Java
- **UI Framework:** Java Swing
- **Concepts:** Multithreading, Concurrency Control, Synchronization Primitives, Thread Safety
