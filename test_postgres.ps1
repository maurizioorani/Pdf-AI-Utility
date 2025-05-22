# PowerShell script to test PostgreSQL connection

# Check if psql is available
$psqlExists = $null -ne (Get-Command "psql" -ErrorAction SilentlyContinue)

if (-not $psqlExists) {
    Write-Host "❌ PostgreSQL client (psql) not found in PATH" -ForegroundColor Red
    Write-Host "👉 Please install PostgreSQL or add psql to your PATH" -ForegroundColor Yellow
    exit 1
}

# Set connection parameters
$PGHOST = "localhost"
$PGPORT = "5432"
$PGUSER = "postgres"
$PGPASSWORD = "test123"
$PGDATABASE = "default_database"

# Print connection information
Write-Host "Testing PostgreSQL connection..." -ForegroundColor Cyan
Write-Host "Host: $PGHOST"
Write-Host "Port: $PGPORT"
Write-Host "User: $PGUSER"
Write-Host "Database: $PGDATABASE"
Write-Host ""

# Set environment variables for psql
$env:PGPASSWORD = $PGPASSWORD

# Try to connect to PostgreSQL and execute a test query
Write-Host "Testing connection to PostgreSQL database..." -ForegroundColor Cyan
try {
    $version = psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "SELECT version();" -t 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Successfully connected to PostgreSQL database!" -ForegroundColor Green
        
        # Check if tables exist in the database
        Write-Host ""
        Write-Host "Checking for OCR documents table..." -ForegroundColor Cyan
        $tableCount = (psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ocr_documents';" | Out-String).Trim()
        
        if ($tableCount -gt 0) {
            Write-Host "✅ OCR documents table exists" -ForegroundColor Green
            
            # Count documents in the table
            $docCount = (psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM ocr_documents;" | Out-String).Trim()
            Write-Host "📝 Found $docCount OCR documents in the database" -ForegroundColor Cyan
            
            # Show a sample of documents if any exist
            if ($docCount -gt 0) {
                Write-Host ""
                Write-Host "Sample of stored OCR documents:" -ForegroundColor Cyan
                psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "SELECT id, original_filename, language_used, is_enhanced, enhancement_model, document_type, created_at FROM ocr_documents ORDER BY created_at DESC LIMIT 5;"
            }
        } else {
            Write-Host "❌ OCR documents table does not exist yet" -ForegroundColor Yellow
            Write-Host "👉 Run the application with the postgres profile to create tables automatically" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ Failed to connect to PostgreSQL database!" -ForegroundColor Red
        Write-Host "👉 Make sure Docker is running and the PostgreSQL container is up" -ForegroundColor Yellow
        Write-Host "👉 Run 'docker-compose up -d' to start PostgreSQL" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Error connecting to PostgreSQL: $_" -ForegroundColor Red
    Write-Host "👉 Make sure Docker is running and the PostgreSQL container is up" -ForegroundColor Yellow
    Write-Host "👉 Run 'docker-compose up -d' to start PostgreSQL" -ForegroundColor Yellow
}

# Clear the environment variable
Remove-Item Env:\PGPASSWORD
