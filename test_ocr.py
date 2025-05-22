import requests
import os
import sys

def test_ocr(file_path, language='eng'):
    """
    Tests OCR functionality by sending a file to the OCR endpoint.
    """
    url = 'http://localhost:8080/ocr/process'
    
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist.")
        return
    
    print(f"Testing OCR with file: {file_path}")
    print(f"Using language: {language}")
    
    # Prepare the file for upload
    files = {'imageFile': open(file_path, 'rb')}
    data = {'language': language}
    
    try:
        # Make the POST request
        print(f"Sending request to {url}...")
        response = requests.post(url, files=files, data=data, allow_redirects=False)
        
        # Check the response
        if response.status_code == 302:  # Redirect
            print(f"Request successful. Redirecting to: {response.headers.get('Location')}")
            
            # Extract flash attributes if present in cookies
            cookies = response.cookies
            for cookie in cookies:
                print(f"Cookie: {cookie.name} = {cookie.value}")
            
            print("Headers:")
            for header, value in response.headers.items():
                print(f"{header}: {value}")
            
            print("\nThe OCR process was initiated successfully.")
            print("Check the web interface at http://localhost:8080/ocr to see the results.")
        else:
            print(f"Error: Unexpected status code {response.status_code}")
            print(response.text)
    except Exception as e:
        print(f"Error during OCR test: {e}")
    finally:
        # Close the file
        files['imageFile'].close()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python test_ocr.py <file_path> [language]")
        sys.exit(1)
    
    file_path = sys.argv[1]
    language = sys.argv[2] if len(sys.argv) > 2 else 'eng'
    
    test_ocr(file_path, language)
