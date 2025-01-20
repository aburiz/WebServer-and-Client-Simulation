
---

# WebServer and Client Simulation Project

## Overview

This project was developed as part of my Operating System Design course. It implements a multithreaded web server and client simulation to demonstrate concepts such as:

- Thread management
- Synchronization
- Deadlock prevention
- Concurrent account transfers

The server handles client connections, processes transfer requests between bank accounts, and ensures data integrity through robust synchronization mechanisms.

## Features

- **Multithreading**: A thread pool efficiently handles concurrent client requests.
- **Synchronization**: Reentrant locks and semaphores ensure safe access to shared resources.
- **Deadlock Prevention**: Consistent lock acquisition and fine-grained locking strategies avoid deadlock scenarios.
- **Error Handling**: The server validates inputs and ensures smooth operation under various edge cases.

## Files in This Project

### Source Code

- **WebServer.java**: Implements the multithreaded web server using a thread pool and handles client requests securely.
- **Account.java**: Represents a bank account with operations for deposit, withdrawal, and balance management.
- **SimpleWebClient.java**: A basic client implementation that sends transfer requests to the server.
- **SimpleWebClientDeadlock.java**: Simulates potential deadlock scenarios by creating multiple concurrent client threads.

### Report

- **WebServer_Design_Report.pdf**: A comprehensive design document detailing data structures, algorithms, synchronization strategies, and testing scenarios.

## How It Works

### Server

1. The server initializes accounts from a file and starts listening for client connections.
2. Each client request is processed in a separate thread from a thread pool.
3. Account transfers are handled using locks to ensure thread safety.
4. The server updates account balances in a synchronized manner to maintain consistency.

### Client

1. Clients connect to the server using sockets.
2. Transfer requests are sent using HTTP-like POST messages.
3. Responses from the server are displayed on the client console.

### Synchronization Techniques

- **Reentrant Locks**: Ensures safe transfer operations without race conditions.
- **Semaphores**: Used for synchronizing access to the accounts file.
- **Thread Pool**: Limits the number of active threads, reducing resource contention.

### Error Handling

- Invalid account numbers or negative values are rejected.
- Transfers with insufficient funds are blocked.
- Deadlock scenarios are mitigated by consistent lock acquisition order.

## How to Run

### Prerequisites

- Java Development Kit (JDK) installed.
- A text file containing account data (default path: `src/main/resources/accounts.txt`).

### Steps

1. Compile the code:

   ```bash
   javac ca/concordia/server/*.java ca/concordia/client/*.java
   ```

2. Run the server:

   ```bash
   java ca.concordia.server.WebServer
   ```

3. Run a client:

   ```bash
   java ca.concordia.client.SimpleWebClient
   ```

4. (Optional) Simulate deadlocks:

   ```bash
   java ca.concordia.client.SimpleWebClientDeadlock
   ```

### Testing Scenarios

The server and client are tested for:

- Successful and failed transfers.
- Concurrent transfer operations.
- Deadlock and race condition prevention.

Refer to the design report for detailed test scenarios and outcomes.

## Future Improvements

- Implement a web-based front end for better user interaction.
- Optimize the thread pool for dynamic scaling.
- Enhance logging with structured formats like JSON for better monitoring.
