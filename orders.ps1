param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host '1. POST /customers'
$customer = Invoke-RestMethod -Method Post -Uri "$BaseUrl/customers" `
    -ContentType 'application/json' -Body '{"name":"Jan Kowalski"}'
$customer | ConvertTo-Json

Write-Host '2. GET /customers'
$customers = Invoke-RestMethod -Uri "$BaseUrl/customers"
ConvertTo-Json -InputObject @($customers) -Depth 5

Write-Host '3. POST /orders'
$body = @{
    customerId = $customer.id
    productName = 'Notes'
    quantity = 2
    unitPrice = 19.99
} | ConvertTo-Json
$order = Invoke-RestMethod -Method Post -Uri "$BaseUrl/orders" `
    -ContentType 'application/json' -Body $body
$order | ConvertTo-Json

Write-Host "4. GET /orders/$($order.id)"
Invoke-RestMethod -Uri "$BaseUrl/orders/$($order.id)" | ConvertTo-Json

Write-Host "5. GET /audit/orders/$($order.id)"
$history = Invoke-RestMethod -Uri "$BaseUrl/audit/orders/$($order.id)"
ConvertTo-Json -InputObject @($history) -Depth 5

Write-Host 'Historia jest zapisywana asynchronicznie. Ponowny odczyt:'
Write-Host ('$history = Invoke-RestMethod -Uri "{0}/audit/orders/{1}"; ConvertTo-Json -InputObject @($history)' -f $BaseUrl, $order.id)
