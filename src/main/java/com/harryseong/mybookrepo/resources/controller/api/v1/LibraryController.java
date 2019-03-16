package com.harryseong.mybookrepo.resources.controller.api.v1;

import com.harryseong.mybookrepo.resources.ResourcesApplication;
import com.harryseong.mybookrepo.resources.domain.Book;
import com.harryseong.mybookrepo.resources.domain.User;
import com.harryseong.mybookrepo.resources.dto.BookDTO;
import com.harryseong.mybookrepo.resources.repository.BookRepository;
import com.harryseong.mybookrepo.resources.repository.RoleRepository;
import com.harryseong.mybookrepo.resources.repository.UserRepository;
import com.harryseong.mybookrepo.resources.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;


@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/library")
public class LibraryController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesApplication.class);

    @Autowired
    BookService bookService;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRepository userRepository;

    @GetMapping("/books")
    public List<Book> getAllBooks(@RequestParam(name = "userId") Integer userId) {

        User user = userRepository.findById(userId).get();
        return bookRepository.findAllByUsersContaining(user);
    }

    @PostMapping("/book")
    public ResponseEntity<String> addBook(@RequestBody @Valid BookDTO bookDTO, @RequestParam(name = "userId") Integer userId) {
        Book book = null;
        if (bookDTO.getIsbn10() != null) {
            book = bookRepository.findByIsbn10(bookDTO.getIsbn10());
        }
        if (book == null && bookDTO.getIsbn13() != null) {
            book = bookRepository.findByIsbn13(bookDTO.getIsbn13());
        }
        if (book == null && bookDTO.getOtherIdType() != null) {
            book = bookRepository.findByOtherIdType(bookDTO.getOtherIdType());
        }

        if (book == null) {
            book = bookService.updateBook(new Book(), bookDTO);
            try {
                bookRepository.save(book);
                LOGGER.info("New book saved successfully: {}", book.getTitle());
            } catch (UnexpectedRollbackException e) {
                LOGGER.error("Unable to save new book, {}, due to db error: {}", book.getTitle(), e.getMostSpecificCause());
            }
        } else {
            LOGGER.info("Book already exists in db: {}", bookDTO.getTitle());
        }

        User user = userRepository.findById(userId).get();
        if (user.getBooks().contains(book)) {
            LOGGER.info("Book, {}, already in {}'s library.", book.getTitle(), user.getFullName());
            return new ResponseEntity<>(String.format("Book, %s, already in %s's library.", book.getTitle(), user.getFullName()), HttpStatus.ACCEPTED);
        } else {
            user.addBook(book);

            try {
                userRepository.save(user);
                LOGGER.info("Book, {}, added to {}'s library.", book.getTitle(), user.getFullName());
                return new ResponseEntity<>(String.format("New book saved successfully: %s.", book.getTitle()), HttpStatus.CREATED);
            } catch (UnexpectedRollbackException e) {
                LOGGER.error("Unable to add book, {}, to {}'s library due to db error: {}.", book.getTitle(), user.getFullName(), e.getMostSpecificCause());
                return new ResponseEntity<>(String.format("Unable to add book, %s, to %s's library due to db error.", book.getTitle(), user.getFullName()), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @DeleteMapping("/book")
    private ResponseEntity<String> removeBook(@RequestParam(name = "userId") Integer userId, @RequestParam(name = "bookId") Integer bookId) {
        this.userRepository.findById(userId)
                .ifPresent(user -> this.bookRepository.findById(bookId)
                        .ifPresent(book -> {
                            user.removeBook(book);
                            this.userRepository.save(user);
                        })
                );
        LOGGER.info("Book {} was removed from user {} library. ", bookId, userId);
        return new ResponseEntity<>(String.format("Book, %s, was removed from user %s library.", bookId, userId), HttpStatus.ACCEPTED);
    }
}