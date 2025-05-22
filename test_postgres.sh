#!/bin/bash

# Set environment variables for the connection
export PGHOST=localhost
export PGPORT=5432
export PGUSER=postgres
export PGPASSWORD=test123
export PGDATABASE=default_database

# Print environment information
echo "Testing PostgreSQL connection..."
echo "Host: $PGHOST"
echo "Port: $PGPORT"
echo "User: $PGUSER"
echo "Database: $PGDATABASE"
echo

# Try to connect to PostgreSQL and execute a test query
echo "Testing connection to PostgreSQL database..."
if psql -c "SELECT version();" > /dev/null 2>&1; then
    echo "✅ Successfully connected to PostgreSQL database!"
    
    # Check if tables exist in the database
    echo
    echo "Checking for OCR documents table..."
    TABLE_COUNT=$(psql -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ocr_documents';" | xargs)
    
    if [ "$TABLE_COUNT" -gt "0" ]; then
        echo "✅ OCR documents table exists"
        
        # Count documents in the table
        DOC_COUNT=$(psql -t -c "SELECT COUNT(*) FROM ocr_documents;" | xargs)
        echo "📝 Found $DOC_COUNT OCR documents in the database"
        
        # Show a sample of documents if any exist
        if [ "$DOC_COUNT" -gt "0" ]; then
            echo
            echo "Sample of stored OCR documents:"
            psql -c "SELECT id, original_filename, language_used, is_enhanced, enhancement_model, document_type, created_at FROM ocr_documents ORDER BY created_at DESC LIMIT 5;"
        fi
    else
        echo "❌ OCR documents table does not exist yet"
        echo "👉 Run the application with the postgres profile to create tables automatically"
    fi
else
    echo "❌ Failed to connect to PostgreSQL database!"
    echo "👉 Make sure Docker is running and the PostgreSQL container is up"
    echo "👉 Run 'docker-compose up -d' to start PostgreSQL"
fi
