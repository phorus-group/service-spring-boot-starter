Feature: Transaction management and query optimization
  CrudService wraps all operations in explicit transactions via TransactionTemplate. This keeps the
  Hibernate session open so Mapper can traverse lazy-loaded relationships, and ensures that batch
  fetching cooperates with the library's transaction wrapping.

  The DTO structure drives what Mapper accesses: if a response DTO doesn't declare a field for a
  lazy collection, Mapper never touches it and no query fires.

  Scenario: DTO without lazy field skips the collection query
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    And the given Address exists:
      | address |
      | addr2   |
    And Hibernate statistics are reset
    When the GET "/user/{userId}" endpoint is called
    Then the service returns HTTP 200
    And at most 2 prepared statements were executed

  Scenario: DTO with lazy field loads the collection inside the transaction
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    And the given Address exists:
      | address |
      | addr2   |
    And the given Address exists:
      | address |
      | addr3   |
    And Hibernate statistics are reset
    When the GET "/user-detail/{userId}" endpoint is called
    Then the service returns HTTP 200
    And the User detail response has 3 addresses
    And at most 3 prepared statements were executed

  Scenario: Batch fetching loads 10 addresses in bounded queries
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And 10 Addresses exist for the current User
    And Hibernate statistics are reset
    When the GET "/user-detail/{userId}" endpoint is called
    Then the service returns HTTP 200
    And the User detail response has 10 addresses
    And at most 4 prepared statements were executed

  Scenario: transactionalMapTo joins an existing transaction instead of opening a new session
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    And Hibernate statistics are reset
    When transactionalMapTo is called within an existing read-only transaction
    Then the mapped User detail includes addresses
    And at most 1 Hibernate session was opened

  Scenario: transactionalMapTo creates its own transaction when called outside one
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    When a detached User is mapped with transactionalMapTo to a DTO without lazy fields
    Then the mapping succeeds with the correct User data

  Scenario: Detached entity with uninitialized lazy collection fails outside its transaction
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    When a detached User is mapped with transactionalMapTo to a DTO with lazy fields
    Then the mapping fails with a lazy initialization error

  Scenario: Load and map in the same transaction uses a single session
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    And Hibernate statistics are reset
    When a User is loaded and mapped within a single read-only transaction
    Then the mapped User detail includes addresses
    And at most 1 Hibernate session was opened

  Scenario: fetchAndMapTo loads and maps in a single transaction
    Given the given User exists:
      | name     | surname |
      | testUser | sursur  |
    And the given Address exists:
      | address |
      | addr1   |
    And Hibernate statistics are reset
    When fetchAndMapTo is used to load and map a User
    Then the mapped User detail includes addresses
    And at most 1 Hibernate session was opened
