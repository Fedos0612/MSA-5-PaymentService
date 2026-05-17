# Task 2. Таблица переходов State Machine для платежной операции OrchestrPay

| Исходное состояние | Переходное состояние | Событие |
|---|---|---|
| `NEW` | `PAYMENT_CREATED` | Пользователь инициировал платёж, создана платёжная операция |
| `PAYMENT_CREATED` | `DEBIT_IN_PROGRESS` | Запущено списание денежных средств со счёта клиента |
| `DEBIT_IN_PROGRESS` | `DEBITED` | Деньги успешно списаны со счёта клиента |
| `DEBIT_IN_PROGRESS` | `FAILED` | Списание не выполнено: недостаточно средств, ошибка банка или платёжного шлюза |
| `DEBITED` | `FRAUD_CHECK_IN_PROGRESS` | Запущена антифрод-проверка |
| `FRAUD_CHECK_IN_PROGRESS` | `APPROVED` | Антифрод вернул решение `ALLOW` |
| `FRAUD_CHECK_IN_PROGRESS` | `REJECTED_BY_FRAUD` | Антифрод вернул решение `DENY` |
| `FRAUD_CHECK_IN_PROGRESS` | `WAITING_MANUAL_REVIEW` | Антифрод вернул решение `MANUAL_REVIEW` |
| `FRAUD_CHECK_IN_PROGRESS` | `APPROVED_BY_CUTOFF` | Сработал cut-off timer: сервисы проверок не вернули ответ вовремя, транзакция по умолчанию разрешена |
| `WAITING_MANUAL_REVIEW` | `APPROVED` | Оператор ручной проверки подтвердил операцию в течение 20 минут |
| `WAITING_MANUAL_REVIEW` | `REJECTED_BY_FRAUD` | Оператор ручной проверки отклонил операцию |
| `WAITING_MANUAL_REVIEW` | `APPROVED_BY_CUTOFF` | Истекло время ожидания ручной проверки, применено правило cut-off |
| `APPROVED` | `TRANSFER_IN_PROGRESS` | Запущен перевод денежных средств контрагенту |
| `APPROVED_BY_CUTOFF` | `TRANSFER_IN_PROGRESS` | Запущен перевод денежных средств контрагенту после cut-off-разрешения |
| `TRANSFER_IN_PROGRESS` | `SUCCEEDED` | Деньги успешно переведены контрагенту |
| `TRANSFER_IN_PROGRESS` | `REFUND_IN_PROGRESS` | Перевод контрагенту не выполнен, требуется вернуть деньги клиенту |
| `REJECTED_BY_FRAUD` | `BLOCKED` | Операция признана подозрительной и заблокирована |
| `BLOCKED` | `REFUND_IN_PROGRESS` | Запущен автоматический возврат денег клиенту |
| `REFUND_IN_PROGRESS` | `REFUNDED` | Деньги успешно возвращены клиенту |
| `REFUND_IN_PROGRESS` | `REFUND_RETRY_PENDING` | Возврат не выполнен из-за временной ошибки, требуется повторная попытка |
| `REFUND_RETRY_PENDING` | `REFUND_IN_PROGRESS` | Запущен retry возврата |
| `REFUND_RETRY_PENDING` | `REFUND_FAILED` | Исчерпаны попытки автоматического возврата, требуется техническая эскалация |
| `REFUNDED` | `CUSTOMER_NOTIFIED` | Клиент уведомлён об отклонении операции и возврате денег |
| `SUCCEEDED` | `CUSTOMER_NOTIFIED` | Клиент уведомлён об успешном платеже |
| `BLOCKED` | `SECURITY_NOTIFIED` | В систему безопасности отправлено уведомление о подозрительной операции |
| `CUSTOMER_NOTIFIED` | `COMPLETED` | Процесс платежа завершён после уведомления клиента |
| `SECURITY_NOTIFIED` | `COMPLETED` | Процесс обработки подозрительной операции завершён после уведомления безопасности |
| `FAILED` | `COMPLETED` | Процесс завершён без списания денег |
| `REFUND_FAILED` | `MANUAL_ESCALATION_REQUIRED` | Не удалось автоматически вернуть деньги, требуется разбор инцидента |