# Gutenberg Book Explorer

A Java 17 console application that uses JSoup to explore and search Project Gutenberg books.

## Requirements

- JDK 17 or newer
- Maven

## Build and run

```bash
mvn clean compile
mvn compile exec:java
```

The application menu lets you explore popular books, search by title or author, add books to a fixed-size reading list of 20 slots, view details, and remove books. Internet access is required for live Gutenberg searches.

## Features

- Scrapes the Project Gutenberg top-books and search pages with JSoup.
- Keeps search results in a temporary fixed-size array and reading-list books in a separate fixed-size `Book[]` array.
- Validates menu input, result selections, duplicate additions, list capacity, and network failures.
- Removes reading-list books by shifting the remaining elements left.
