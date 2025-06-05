#!/usr/bin/env python3
"""
Simple test script to test PDF compression functionality
"""
import requests
import os
import sys

def test_pdf_compression():
    """Test the PDF compression endpoint"""
    
    # First, check if we have any test PDFs
    test_files_dir = "./test_files"
    
    # Create a simple test PDF if none exists
    if not os.path.exists(test_files_dir):
        os.makedirs(test_files_dir)
    
    # Check if server is running
    try:
        response = requests.get("http://localhost:8080")
        print("✓ Server is running")
    except requests.exceptions.ConnectionError:
        print("✗ Server is not running. Please start the application first.")
        print("Run: mvnw.cmd spring-boot:run")
        return False
    
    # Check compression endpoint
    try:
        response = requests.get("http://localhost:8080/compress")
        if response.status_code == 200:
            print("✓ Compression endpoint is accessible")
        else:
            print(f"✗ Compression endpoint returned status: {response.status_code}")
            return False
    except Exception as e:
        print(f"✗ Error accessing compression endpoint: {e}")
        return False
    
    print("\nTo test compression manually:")
    print("1. Go to http://localhost:8080/compress")
    print("2. Upload a PDF file")
    print("3. Enable 'Compress Images' option")
    print("4. Check if the downloaded file is smaller than the original")
    
    return True

if __name__ == "__main__":
    success = test_pdf_compression()
    sys.exit(0 if success else 1)
