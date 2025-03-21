package io.jexxa.jexxatemplate.integration;

import io.jexxa.infrastructure.drivingadapter.messaging.JMSConfiguration;
import io.jexxa.jexxatemplate.JexxaTemplate;
import io.jexxa.jexxatemplate.applicationservice.BookStoreService;
import io.jexxa.jexxatemplate.domain.book.BookSoldOut;
import io.jexxa.jexxatemplate.domain.book.ISBN13;
import io.jexxa.jexxatest.JexxaIntegrationTest;
import io.jexxa.jexxatest.integrationtest.messaging.MessageBinding;
import io.jexxa.jexxatest.integrationtest.rest.RESTBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.jexxa.jexxatemplate.domain.book.ISBN13.createISBN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JexxaTemplateIT
{
    static private final String addToStock = "addToStock";
    static private final String amountInStock = "amountInStock";
    static private final String sell = "sell";
    private static final ISBN13 ANY_BOOK = createISBN("978-3-86490-387-8" );


    private static JexxaIntegrationTest jexxaIntegrationTest;  // Simplified IT testing with jexxa-test
    private static RESTBinding restBinding;                    // Binding to access application under test via REST
    private static MessageBinding messageBinding;              // Binding to access application under test via JMS

    @BeforeAll
    static void initBeforeAll()
    {
        jexxaIntegrationTest = new JexxaIntegrationTest(JexxaTemplate.class);
        messageBinding = jexxaIntegrationTest.getMessageBinding();
        restBinding = jexxaIntegrationTest.getRESTBinding();
    }


    @Test
    void testStartupApplication()
    {
        //Arrange -
        var boundedContext = restBinding.getBoundedContext();

        //Act / Assert
        var result = boundedContext.contextName();

        //Assert
        assertEquals(JexxaTemplate.class.getSimpleName(), result);
    }

    @Test
    void testAddBook()
    {
        //Arrange
        var bookStoreService = restBinding.getRESTHandler(BookStoreService.class);

        var addedBooks = 5;
        var inStock = bookStoreService.postRequest(Integer.class, amountInStock, ANY_BOOK );

        //Act
        bookStoreService.postRequest(Void.class, addToStock, ANY_BOOK, addedBooks);
        var result = bookStoreService.postRequest(Integer.class, amountInStock, ANY_BOOK );

        //Assert
        assertEquals(inStock + addedBooks, result);
    }

    @Test
    void testSellLastBook()
    {
        //Arrange
        var bookStoreService = restBinding.getRESTHandler(BookStoreService.class);
        var messageListener = messageBinding.getMessageListener("BookStoreTopic", JMSConfiguration.MessagingType.TOPIC);

        bookStoreService.postRequest(Void.class, addToStock, ANY_BOOK, 5);
        var inStock = bookStoreService.postRequest(Integer.class, amountInStock, ANY_BOOK );

        //Act
        for (int i = 0; i < inStock; ++i)
        {
            bookStoreService.postRequest(Void.class, sell, ANY_BOOK); // Sell all books in stock
        }

        // Receive the jms message
        var result = messageListener
                .awaitMessage(5, TimeUnit.SECONDS)
                .pop(BookSoldOut.class);

        //Assert
        assertEquals(ANY_BOOK, result.isbn13());
    }

    @AfterAll
    static void shutDown()
    {
        jexxaIntegrationTest.shutDown();
    }
}
