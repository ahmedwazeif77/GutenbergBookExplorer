package com.student;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class App {
    private static final int READING_LIST_SIZE = 20;
    private static final int TEMP_RESULTS_SIZE = 100;
    private static final Pattern EBOOK_LINK = Pattern.compile("/ebooks/(\\d+)(?:[^0-9].*)?$");

    private final Scanner scanner = new Scanner(System.in);
    private final Book[] readingList = new Book[READING_LIST_SIZE];
    // Search and explore results are temporary and do not change the reading list.
    private Book[] tempResults = new Book[TEMP_RESULTS_SIZE];
    private int readingCount;

    public static void main(String[] args) {
        new App().run();
    }

    public void run() {
        int choice;
        do {
            printMenu();
            choice = readInt("Enter your choice: ");

            if (choice == 1) {
                exploreBooks();
            } else if (choice == 2) {
                System.out.print("Enter title to search: ");
                searchBook(scanner.nextLine().trim());
            } else if (choice == 3) {
                System.out.print("Enter author to search: ");
                String author = scanner.nextLine().trim();
                searchBook(author, true);
            } else if (choice == 4) {
                addBook();
            } else if (choice == 5) {
                viewReadingList();
            } else if (choice == 6) {
                removeBook();
            } else if (choice == 7) {
                displayBookDetails();
            } else if (choice == 0) {
                System.out.println("Goodbye!");
            } else {
                System.out.println("Invalid menu choice. Please enter a number from 0 to 7.");
            }
        } while (choice != 0);

        scanner.close();
    }

    private void printMenu() {
        System.out.println();
        System.out.println("===== GUTENBERG BOOK EXPLORER =====");
        System.out.println("1. Explore Books");
        System.out.println("2. Search by Title");
        System.out.println("3. Search by Author");
        System.out.println("4. Add Book to Reading List");
        System.out.println("5. View Reading List");
        System.out.println("6. Remove Book");
        System.out.println("7. Display Book Details");
        System.out.println("0. Exit");
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    public void exploreBooks() {
        try {
            Document document = Jsoup.connect("https://www.gutenberg.org/browse/scores/top")
                    .userAgent("GutenbergBookExplorer/1.0")
                    .referrer("https://www.gutenberg.org/")
                    .timeout(15000)
                    .get();

            Set<String> seen = new HashSet<>();
            tempResults = new Book[TEMP_RESULTS_SIZE];
            int resultCount = 0;

            Elements links = document.select("a[href]");
            for (Element link : links) {
                String absUrl = link.absUrl("href");
                String ebookNumber = getEbookNumber(absUrl);
                if (ebookNumber == null) {
                    continue;
                }

                String key = "/ebooks/" + ebookNumber;
                if (seen.contains(key)) {
                    continue;
                }

                String title = cleanText(link.text());
                if (title.isEmpty()) {
                    continue;
                }

                title = title.replaceAll("\\s*\\(.*\\)$", "").trim();
                if (title.toLowerCase().startsWith("bookshelf")) {
                    continue;
                }

                seen.add(key);
                String author = extractAuthor(title);
                tempResults[resultCount++] = new Book(title, author, "https://www.gutenberg.org/ebooks/" + ebookNumber);
                if (resultCount >= 20) {
                    break;
                }
            }

            if (resultCount < 10) {
                searchBook("fiction", false);
                return;
            }

            displayTemporaryResults(resultCount);
            System.out.println(resultCount + " books found.");
        } catch (IOException exception) {
            tempResults = new Book[TEMP_RESULTS_SIZE];
            System.out.println("Connection error: Could not reach Project Gutenberg. Check your internet connection.");
        }
    }

    public void searchBook(String title) {
        searchBook(title, false);
    }

    public void searchBook(String keyword, boolean searchByAuthor) {
        if (keyword == null || keyword.trim().isEmpty()) {
            tempResults = new Book[TEMP_RESULTS_SIZE];
            System.out.println("Search cannot be empty.");
            return;
        }

        try {
            String encoded = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            String url = "https://www.gutenberg.org/ebooks/search/?query=" + encoded;
            Document document = Jsoup.connect(url)
                    .userAgent("GutenbergBookExplorer/1.0")
                    .timeout(15000)
                    .get();

            tempResults = new Book[TEMP_RESULTS_SIZE];
            Set<String> seen = new HashSet<>();
            int resultCount = 0;

            Elements books = document.select("li.booklink");
            for (Element book : books) {
                Element titleElement = book.selectFirst("span.title");
                Element subtitleElement = book.selectFirst("span.subtitle");
                Element linkElement = book.selectFirst("a[href]");
                if (titleElement == null || linkElement == null) {
                    continue;
                }

                String absUrl = linkElement.absUrl("href");
                String ebookNumber = getEbookNumber(absUrl);
                if (ebookNumber == null) {
                    continue;
                }

                String key = "/ebooks/" + ebookNumber;
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);

                String bookTitle = cleanText(titleElement.text());
                String author = subtitleElement == null
                        ? "Unknown"
                        : cleanAuthor(subtitleElement.text());

                if (searchByAuthor) {
                    if (!author.toLowerCase().contains(keyword.trim().toLowerCase())) {
                        continue;
                    }
                } else if (!bookTitle.toLowerCase().contains(keyword.toLowerCase())) {
                    continue;
                }

                tempResults[resultCount++] = new Book(
                        bookTitle,
                        author,
                        "https://www.gutenberg.org/ebooks/" + ebookNumber);
                if (resultCount >= TEMP_RESULTS_SIZE) {
                    break;
                }
            }

            // Fallback for pages where the li.booklink selector differs.
            if (resultCount == 0) {
                Elements links = document.select("a[href]");
                for (Element link : links) {
                    String absUrl = link.absUrl("href");
                    String ebookNumber = getEbookNumber(absUrl);
                    if (ebookNumber == null) {
                        continue;
                    }

                    String key = "/ebooks/" + ebookNumber;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);

                    String bookTitle = cleanText(link.text());
                    if (bookTitle.isEmpty() || bookTitle.length() > 200) {
                        continue;
                    }
                    if (!searchByAuthor && !bookTitle.toLowerCase().contains(keyword.toLowerCase())) {
                        continue;
                    }

                    tempResults[resultCount++] = new Book(
                            bookTitle,
                            "Unknown",
                            "https://www.gutenberg.org/ebooks/" + ebookNumber);
                    if (resultCount >= TEMP_RESULTS_SIZE) {
                        break;
                    }
                }
            }

            if (resultCount == 0) {
                System.out.println("No results found for \"" + keyword + "\".");
                return;
            }

            displayTemporaryResults(resultCount);
            System.out.println(resultCount + " results found.");
        } catch (IOException exception) {
            tempResults = new Book[TEMP_RESULTS_SIZE];
            System.out.println("Connection error: Could not reach Project Gutenberg. Check your internet connection.");
        } catch (IllegalArgumentException exception) {
            tempResults = new Book[TEMP_RESULTS_SIZE];
            System.out.println("Invalid search input.");
        }
    }

    public void addBook() {
        int resultCount = getTemporaryResultCount();
        if (resultCount == 0) {
            System.out.println("No search or explore results are available. Search or explore first.");
            return;
        }
        if (readingCount >= READING_LIST_SIZE) {
            System.out.println("Reading list is full. Remove a book before adding another.");
            return;
        }

        int selection = readInt("Enter the result number to add: ");
        if (selection < 1 || selection > resultCount) {
            System.out.println("Out-of-range selection.");
            return;
        }

        Book selected = tempResults[selection - 1];
        // Use the URL as the identity so duplicate books cannot be added twice.
        for (int i = 0; i < readingCount; i++) {
            if (readingList[i].getUrl().equals(selected.getUrl())) {
                System.out.println("That book is already in the reading list.");
                return;
            }
        }

        readingList[readingCount++] = selected;
        System.out.println("Added: " + selected.getTitle());
    }

    public void viewReadingList() {
        if (readingCount == 0) {
            System.out.println("The reading list is empty.");
            return;
        }

        System.out.println("===== READING LIST =====");
        for (int i = 0; i < readingCount; i++) {
            System.out.println((i + 1) + ". " + readingList[i]);
        }
    }

    public void removeBook() {
        if (readingCount == 0) {
            System.out.println("The reading list is empty.");
            return;
        }

        viewReadingList();
        int selection = readInt("Enter the reading-list number to remove: ");
        if (selection < 1 || selection > readingCount) {
            System.out.println("Out-of-range selection.");
            return;
        }

        int index = selection - 1;
        String removedTitle = readingList[index].getTitle();
        // Shift each later book one position left to keep the array gap-free.
        for (int i = index; i < readingCount - 1; i++) {
            readingList[i] = readingList[i + 1];
        }
        readingList[--readingCount] = null;
        System.out.println("Removed: " + removedTitle);
    }

    public void displayBookDetails() {
        if (getTemporaryResultCount() > 0) {
            int selection = readInt("Enter a search/explore result number: ");
            int resultCount = getTemporaryResultCount();
            if (selection < 1 || selection > resultCount) {
                System.out.println("Out-of-range selection.");
                return;
            }
            printBookDetails(tempResults[selection - 1]);
        } else if (readingCount > 0) {
            viewReadingList();
            int selection = readInt("Enter a reading-list number: ");
            if (selection < 1 || selection > readingCount) {
                System.out.println("Out-of-range selection.");
                return;
            }
            printBookDetails(readingList[selection - 1]);
        } else {
            System.out.println("No books available. Explore or search first.");
        }
    }

    private void printBookDetails(Book book) {
        System.out.println("\n===== BOOK DETAILS =====");
        System.out.println("Title: " + book.getTitle());
        System.out.println("Author: " + book.getAuthor());
        System.out.println("URL: " + book.getUrl());
    }

    private void displayTemporaryResults(int resultCount) {
        System.out.println("\n===== SEARCH / EXPLORE RESULTS =====");
        for (int i = 0; i < resultCount; i++) {
            System.out.println((i + 1) + ". " + tempResults[i]);
        }
    }

    private int getTemporaryResultCount() {
        int count = 0;
        for (int i = 0; i < tempResults.length && tempResults[i] != null; i++) {
            count++;
        }
        return count;
    }

    private String getEbookNumber(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            Matcher matcher = Pattern.compile("/ebooks/(\\d+)").matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

    private String extractAuthor(String title) {
        if (title.toLowerCase().contains(" by ")) {
            int position = title.toLowerCase().lastIndexOf(" by ");
            String author = title.substring(position + 4).trim();
            if (!author.isEmpty()) {
                return cleanAuthor(author);
            }
        }
        return "Unknown";
    }

    private String cleanAuthor(String subtitle) {
        String author = cleanText(subtitle);
        author = author.replaceFirst("(?i)^by\\s+", "");
        author = author.replaceAll("\\s*\\([^)]*\\)$", "").trim();
        return author.isEmpty() ? "Unknown" : author;
    }

    private String cleanText(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
