#!/usr/bin/env python3
"""
Manual test to verify PDF compression functionality via HTTP endpoint
"""

import os
import requests
import sys

def test_compression():
    """Test the compression endpoint with our test PDF files"""
    
    # Check if application is running
    try:
        response = requests.get("http://localhost:8080/", timeout=5)
        print(f"✓ Application is running (status: {response.status_code})")
    except requests.exceptions.RequestException as e:
        print(f"✗ Application is not running. Please start it with: mvn spring-boot:run")
        print(f"Error: {e}")
        return False
    
    # Test files to compress
    test_files = [
        "test_files/compression_test.pdf",
        "test_files/large_image_test.pdf"
    ]
    
    # Compression endpoint
    url = "http://localhost:8080/compress"
    
    for test_file in test_files:
        if not os.path.exists(test_file):
            print(f"✗ Test file not found: {test_file}")
            continue
            
        original_size = os.path.getsize(test_file)
        print(f"\nTesting: {test_file}")
        print(f"Original size: {original_size:,} bytes ({original_size/1024:.1f} KB)")
        
        # Test with image compression enabled
        with open(test_file, 'rb') as f:
            files = {'pdfFile': (test_file, f, 'application/pdf')}
            data = {'compressImages': 'on'}  # Enable image compression
            
            try:
                response = requests.post(url, files=files, data=data, timeout=30)
                
                if response.status_code == 200:
                    compressed_size = len(response.content)
                    compression_ratio = (original_size - compressed_size) / original_size * 100
                    
                    print(f"Compressed size: {compressed_size:,} bytes ({compressed_size/1024:.1f} KB)")
                    print(f"Compression ratio: {compression_ratio:.1f}%")
                    
                    if compressed_size < original_size:
                        print(f"✓ Compression successful! Saved {original_size - compressed_size:,} bytes")
                    elif compressed_size == original_size:
                        print(f"⚠ No compression achieved (same size)")
                    else:
                        print(f"⚠ File size increased by {compressed_size - original_size:,} bytes")
                        
                    # Save compressed file for verification
                    compressed_file = test_file.replace('.pdf', '_compressed.pdf')
                    with open(compressed_file, 'wb') as out_f:
                        out_f.write(response.content)
                    print(f"Saved compressed file: {compressed_file}")
                    
                else:
                    print(f"✗ Request failed with status {response.status_code}")
                    print(f"Response: {response.text}")
                    
            except requests.exceptions.RequestException as e:
                print(f"✗ Request failed: {e}")
    
    return True

if __name__ == "__main__":
    print("PDF Compression Test")
    print("=" * 50)
    
    if test_compression():
        print("\n✓ Test completed successfully!")
    else:
        print("\n✗ Test failed!")
        sys.exit(1)
