package com.example.damasuzadmin.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.example.damasuzadmin.databinding.ActivitySignBinding
import com.example.damasuzadmin.models.Admin
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.database.*
import java.util.concurrent.TimeUnit

class SignActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var referenceAdmin: DatabaseReference
    private val TAG = "SignActivity"

    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        auth.setLanguageCode("uz")

        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        firebaseDatabase = FirebaseDatabase.getInstance()
        referenceAdmin = firebaseDatabase.getReference("admins")

        // SMS yuborish tugmasi
        binding.btnSend.setOnClickListener {
            val inputNumber = binding.edtNumber.text.toString()

            if (inputNumber.isEmpty()) {
                Toast.makeText(this, "Raqamni kiriting!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkAdminAndSendSMS(inputNumber)
        }

        // Kodni qo'lda yozish
        binding.edtCode.addTextChangedListener {
            if (it.toString().length == 6) {
                verifyCode()
            }
        }

        // Klaviaturada DONE bosilganda
        binding.edtCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyCode()
                hideKeyboard()
            }
            true
        }
    }

    private fun checkAdminAndSendSMS(phone: String) {
        referenceAdmin.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                var found = false

                // admins ichidagi barcha bolalarni aylanib chiqamiz (1, 2, 3 va hokazo)
                for (adminSnapshot in snapshot.children) {
                    val adminNumber = adminSnapshot.child("number").value?.toString()

                    // Agar ro‘yxatdagi raqam foydalanuvchi kiritgani bilan bir xil bo‘lsa
                    if (adminNumber == phone) {
                        found = true
                        sendVerificationCode(phone)
                        Toast.makeText(
                            this@SignActivity,
                            "$phone raqamiga SMS jo‘natildi",
                            Toast.LENGTH_SHORT
                        ).show()
                        break
                    }
                }

                // Hech bir admin raqami mos kelmasa
                if (!found) {
                    Toast.makeText(
                        this@SignActivity,
                        "Bu raqam admin ro‘yxatida topilmadi!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@SignActivity,
                    "Internetda xatolik: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }



    private fun verifyCode() {
        val code = binding.edtCode.text.toString()

        if (code.length == 6) {
            val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
            signInWithPhoneAuthCredential(credential)
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(TAG, "onVerificationCompleted:$credential")
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "onVerificationFailed", e)

            when (e) {
                is FirebaseAuthInvalidCredentialsException ->
                    Toast.makeText(this@SignActivity, "Raqam formati xato!", Toast.LENGTH_SHORT).show()

                is FirebaseTooManyRequestsException ->
                    Toast.makeText(this@SignActivity, "SMS limiti oshib ketdi!", Toast.LENGTH_SHORT).show()

                else ->
                    Toast.makeText(this@SignActivity, "Xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d(TAG, "onCodeSent:$verificationId")
            storedVerificationId = verificationId
            resendToken = token
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Muvaffaqiyatli!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Kod noto‘g‘ri!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
