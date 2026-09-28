package com.bankms.repository;

import com.bankms.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"user", "branch"})
    Optional<Employee> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "branch"})
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "branch"})
    @Query("""
            select e from Employee e
            where :query is null
               or lower(e.user.fullName) like lower(concat('%', :query, '%'))
               or lower(e.user.email) like lower(concat('%', :query, '%'))
               or lower(e.employeeCode) like lower(concat('%', :query, '%'))
            """)
    Page<Employee> search(@Param("query") String query, Pageable pageable);
}
