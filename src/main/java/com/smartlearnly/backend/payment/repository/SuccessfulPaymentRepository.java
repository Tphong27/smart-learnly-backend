package com.smartlearnly.backend.payment.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SuccessfulPaymentRepository {
    private final JdbcTemplate jdbcTemplate;

    public boolean existsForCourse(UUID transactionId, UUID studentId, UUID courseId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.transactions tx
                    WHERE tx.id = ?
                      AND tx.user_id = ?
                      AND tx.status = 'SUCCESS'::public.tx_status
                      AND (
                          tx.course_id = ?
                          OR EXISTS (
                              SELECT 1
                              FROM public.classes class_offering
                              WHERE class_offering.id = tx.class_id
                                AND class_offering.course_id = ?
                          )
                          OR EXISTS (
                              SELECT 1
                              FROM public.order_items item
                              WHERE item.order_id = tx.order_id
                                AND (
                                    item.course_id = ?
                                    OR EXISTS (
                                        SELECT 1
                                        FROM public.classes class_offering
                                        WHERE class_offering.id = item.class_id
                                          AND class_offering.course_id = ?
                                    )
                                )
                          )
                      )
                )
                """, Boolean.class,
                transactionId,
                studentId,
                courseId,
                courseId,
                courseId,
                courseId);
        return Boolean.TRUE.equals(result);
    }

    public boolean existsForClass(UUID transactionId, UUID studentId, UUID classId) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.transactions tx
                    WHERE tx.id = ?
                      AND tx.user_id = ?
                      AND tx.status = 'SUCCESS'::public.tx_status
                      AND (
                          tx.class_id = ?
                          OR EXISTS (
                              SELECT 1
                              FROM public.order_items item
                              WHERE item.order_id = tx.order_id
                                AND item.class_id = ?
                          )
                      )
                )
                """, Boolean.class, transactionId, studentId, classId, classId);
        return Boolean.TRUE.equals(result);
    }
}
